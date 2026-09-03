package com.bankms.service;

import com.bankms.dto.*;
import com.bankms.entity.AccountType;
import com.bankms.entity.Branch;
import com.bankms.entity.Customer;
import com.bankms.exception.BusinessException;
import com.bankms.repository.BranchRepository;
import com.bankms.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class FundTransferServiceTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private FundTransferService fundTransferService;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private String accountNumber;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin", null, "ROLE_ADMIN"));

        Branch branch = branchRepository.findAll().get(0);
        Customer customer = Customer.builder()
                .username("txn-customer-" + System.nanoTime())
                .fullName("Transaction Tester")
                .dob(LocalDate.of(1992, 3, 3))
                .panNumber("TXNAB" + (1000 + new Random().nextInt(9000)) + "T")
                .build();
        customer = customerRepository.save(customer);

        AccountOpenRequest openRequest = new AccountOpenRequest();
        openRequest.setCustomerUsername(customer.getUsername());
        openRequest.setBranchId(branch.getId());
        openRequest.setAccountType(AccountType.SAVINGS);
        openRequest.setInitialDeposit(BigDecimal.valueOf(5000));
        openRequest.setMinBalance(BigDecimal.valueOf(500));
        openRequest.setInterestRatePercent(BigDecimal.valueOf(4));
        openRequest.setDailyTransferLimit(BigDecimal.valueOf(100_000));

        accountNumber = accountService.openAccount(openRequest).getAccountNumber();
    }

    @Test
    void retryingTheSameIdempotencyKeyDoesNotDoubleDebit() {
        WithdrawalRequest req = new WithdrawalRequest();
        req.setAccountNumber(accountNumber);
        req.setAmount(BigDecimal.valueOf(1000));
        req.setIdempotencyKey("fixed-key-retry-test");

        TransactionResponse first = fundTransferService.withdraw(req);
        TransactionResponse retry = fundTransferService.withdraw(req);

        assertThat(retry.getTransactionRef()).isEqualTo(first.getTransactionRef());
        assertThat(accountService.getByAccountNumber(accountNumber).getBalance()).isEqualByComparingTo("4000");
    }

    @Test
    void withdrawalBreachingMinimumBalanceIsRejected() {
        WithdrawalRequest req = new WithdrawalRequest();
        req.setAccountNumber(accountNumber);
        req.setAmount(BigDecimal.valueOf(4800)); // would leave 200, below minBalance of 500
        req.setIdempotencyKey("min-balance-test");

        assertThatThrownBy(() -> fundTransferService.withdraw(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("minimum balance");

        assertThat(accountService.getByAccountNumber(accountNumber).getBalance()).isEqualByComparingTo("5000");
    }

    @Test
    void transferMovesFundsAtomicallyBetweenTwoAccounts() {
        Branch branch = branchRepository.findAll().get(0);
        Customer other = Customer.builder()
                .username("txn-customer-2-" + System.nanoTime())
                .fullName("Second Tester")
                .dob(LocalDate.of(1990, 6, 6))
                .panNumber("TXNCD" + (1000 + new Random().nextInt(9000)) + "T")
                .build();
        other = customerRepository.save(other);

        AccountOpenRequest openRequest = new AccountOpenRequest();
        openRequest.setCustomerUsername(other.getUsername());
        openRequest.setBranchId(branch.getId());
        openRequest.setAccountType(AccountType.CURRENT);
        openRequest.setInitialDeposit(BigDecimal.ZERO);
        openRequest.setMinBalance(BigDecimal.ZERO);
        openRequest.setDailyTransferLimit(BigDecimal.valueOf(100_000));
        String destAccountNumber = accountService.openAccount(openRequest).getAccountNumber();

        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setSourceAccountNumber(accountNumber);
        transferRequest.setDestinationAccountNumber(destAccountNumber);
        transferRequest.setAmount(BigDecimal.valueOf(1500));
        transferRequest.setIdempotencyKey("transfer-test-" + System.nanoTime());

        fundTransferService.transfer(transferRequest);

        assertThat(accountService.getByAccountNumber(accountNumber).getBalance()).isEqualByComparingTo("3500");
        assertThat(accountService.getByAccountNumber(destAccountNumber).getBalance()).isEqualByComparingTo("1500");
    }
}
