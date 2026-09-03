package com.bankms.service;

import com.bankms.dto.AccountOpenRequest;
import com.bankms.dto.AccountResponse;
import com.bankms.dto.DepositRequest;
import com.bankms.dto.LedgerEntryResponse;
import com.bankms.entity.*;
import com.bankms.exception.BusinessException;
import com.bankms.exception.ResourceNotFoundException;
import com.bankms.repository.AccountRepository;
import com.bankms.repository.CustomerRepository;
import com.bankms.repository.LedgerEntryRepository;
import com.bankms.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final BranchService branchService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final FundTransferService fundTransferService;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public AccountResponse openAccount(AccountOpenRequest request) {
        Customer customer = customerRepository.findByUsername(request.getCustomerUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + request.getCustomerUsername()));
        Branch branch = branchService.getById(request.getBranchId());

        if (request.getAccountType() == AccountType.SAVINGS && request.getInterestRatePercent() == null) {
            throw new BusinessException("Savings accounts require an interestRatePercent");
        }

        String accountNumber = generateAccountNumber(branch);

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .customer(customer)
                .branch(branch)
                .accountType(request.getAccountType())
                .status(AccountStatus.ACTIVE)
                .balance(BigDecimal.ZERO)
                .minBalance(request.getMinBalance())
                .interestRatePercent(request.getAccountType() == AccountType.SAVINGS ? request.getInterestRatePercent() : null)
                .dailyTransferLimit(request.getDailyTransferLimit())
                .build();
        accountRepository.save(account);

        if (request.getInitialDeposit().compareTo(BigDecimal.ZERO) > 0) {
            DepositRequest openingDeposit = new DepositRequest();
            openingDeposit.setAccountNumber(accountNumber);
            openingDeposit.setAmount(request.getInitialDeposit());
            openingDeposit.setIdempotencyKey("OPENING-" + accountNumber);
            openingDeposit.setRemarks("Opening deposit");
            fundTransferService.deposit(openingDeposit);
        }

        return toResponse(accountRepository.findByAccountNumber(accountNumber).orElseThrow());
    }

    @Transactional(readOnly = true)
    public AccountResponse getByAccountNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
        assertViewable(account);
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getMyAccounts() {
        return accountRepository.findByCustomerUsername(currentUserProvider.username()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getStatement(String accountNumber, Pageable pageable) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
        assertViewable(account);

        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(account.getId(), pageable)
                .map(e -> new LedgerEntryResponse(e.getEntryType(), e.getAmount(), e.getBalanceAfter(),
                        e.getDescription(), e.getTransaction().getTransactionRef(), e.getCreatedAt()));
    }

    @Transactional
    public AccountResponse updateStatus(String accountNumber, AccountStatus status) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
        account.setStatus(status);
        accountRepository.save(account);
        return toResponse(account);
    }

    private void assertViewable(Account account) {
        if (currentUserProvider.hasAnyRole("ADMIN", "TELLER")) {
            return;
        }
        if (!account.getCustomer().getUsername().equals(currentUserProvider.username())) {
            throw new BusinessException("You do not have access to this account");
        }
    }

    private String generateAccountNumber(Branch branch) {
        String candidate;
        do {
            candidate = branch.getId() + String.format("%010d", ThreadLocalRandom.current().nextLong(0, 10_000_000_000L));
        } while (accountRepository.findByAccountNumber(candidate).isPresent());
        return candidate;
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(), account.getAccountNumber(), account.getAccountType(), account.getStatus(),
                account.getBalance(), account.getMinBalance(), account.getInterestRatePercent(),
                account.getDailyTransferLimit(), account.getBranch().getBranchName(),
                account.getCustomer().getUsername(), account.getCustomer().getFullName(), account.getCreatedAt());
    }
}
