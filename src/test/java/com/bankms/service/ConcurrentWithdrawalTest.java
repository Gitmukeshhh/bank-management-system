package com.bankms.service;

import com.bankms.dto.AccountOpenRequest;
import com.bankms.dto.AccountResponse;
import com.bankms.dto.WithdrawalRequest;
import com.bankms.entity.AccountType;
import com.bankms.entity.Branch;
import com.bankms.entity.Customer;
import com.bankms.exception.BusinessException;
import com.bankms.repository.BranchRepository;
import com.bankms.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the pessimistic-locking design actually prevents the classic banking-system
 * double-spend bug: two threads racing to withdraw the full balance at the same instant.
 * A naive "read balance, check, write balance" implementation lets both succeed and drives
 * the account negative; this test asserts exactly one of the two wins and the final balance
 * is correct.
 */
@SpringBootTest
class ConcurrentWithdrawalTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private FundTransferService fundTransferService;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void onlyOneOfTwoConcurrentWithdrawalsSucceedsWhenBalanceCoversOnlyOne() throws Exception {
        actAsAdmin();

        Branch branch = branchRepository.findAll().get(0);

        Customer customer = Customer.builder()
                .username("race-customer-" + System.nanoTime())
                .fullName("Race Condition Tester")
                .dob(LocalDate.of(1995, 1, 1))
                .panNumber("RACEX" + (1000 + new Random().nextInt(9000)) + "T")
                .build();
        customer = customerRepository.save(customer);

        AccountOpenRequest openRequest = new AccountOpenRequest();
        openRequest.setCustomerUsername(customer.getUsername());
        openRequest.setBranchId(branch.getId());
        openRequest.setAccountType(AccountType.SAVINGS);
        openRequest.setInitialDeposit(BigDecimal.valueOf(1000));
        openRequest.setMinBalance(BigDecimal.ZERO);
        openRequest.setInterestRatePercent(BigDecimal.valueOf(4));
        openRequest.setDailyTransferLimit(BigDecimal.valueOf(1_000_000));

        AccountResponse account = accountService.openAccount(openRequest);
        String accountNumber = account.getAccountNumber();
        assertThat(account.getBalance()).isEqualByComparingTo("1000");

        int threadCount = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            String idemKey = "race-withdraw-" + i + "-" + System.nanoTime();
            futures.add(pool.submit(() -> {
                actAsAdmin();
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException ignored) {
                }

                WithdrawalRequest req = new WithdrawalRequest();
                req.setAccountNumber(accountNumber);
                req.setAmount(BigDecimal.valueOf(1000));
                req.setIdempotencyKey(idemKey);
                try {
                    fundTransferService.withdraw(req);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    failureCount.incrementAndGet();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }));
        }

        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        AccountResponse after = accountService.getByAccountNumber(accountNumber);
        assertThat(after.getBalance()).isEqualByComparingTo("0");
    }

    private void actAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin", null, "ROLE_ADMIN"));
    }
}
