package com.bankms.service;

import com.bankms.dto.DepositRequest;
import com.bankms.dto.TransactionResponse;
import com.bankms.dto.TransferRequest;
import com.bankms.dto.WithdrawalRequest;
import com.bankms.entity.*;
import com.bankms.exception.BusinessException;
import com.bankms.exception.ResourceNotFoundException;
import com.bankms.repository.AccountRepository;
import com.bankms.repository.LedgerEntryRepository;
import com.bankms.repository.TransactionRepository;
import com.bankms.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * All money movement goes through here. Three properties make this safe under
 * concurrency, unlike a typical "read balance, check, write balance" CRUD flow:
 *
 * 1. Idempotency: every call carries a client-supplied transactionRef; a retried
 *    request (e.g. after a client-side timeout) replays the stored result instead
 *    of re-debiting the account.
 * 2. Pessimistic row locking: the account row is re-fetched with SELECT ... FOR UPDATE
 *    before its balance is read for the business-rule check, so two concurrent requests
 *    against the same account are serialized rather than racing on a stale balance.
 * 3. Deadlock-safe lock ordering: a transfer always locks the lower-id account first,
 *    so two transfers moving money in opposite directions between the same account
 *    pair can never deadlock waiting on each other's locks.
 */
@Service
@RequiredArgsConstructor
public class FundTransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditLogService auditLogService;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public TransactionResponse deposit(DepositRequest request) {
        var existing = transactionRepository.findByTransactionRef(request.getIdempotencyKey());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Long accountId = accountRepository.findIdByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + request.getAccountNumber()));

        Account locked = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        assertOwnershipOrStaff(locked);
        assertActive(locked);

        Transaction txn = transactionRepository.save(Transaction.builder()
                .transactionRef(request.getIdempotencyKey())
                .type(TransactionType.DEPOSIT)
                .destinationAccount(locked)
                .amount(request.getAmount())
                .status(TransactionStatus.SUCCESS)
                .remarks(request.getRemarks())
                .initiatedByUsername(currentUserProvider.username())
                .build());

        BigDecimal newBalance = locked.getBalance().add(request.getAmount());
        locked.setBalance(newBalance);
        accountRepository.save(locked);

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transaction(txn).account(locked).entryType(LedgerEntryType.CREDIT)
                .amount(request.getAmount()).balanceAfter(newBalance).description(request.getRemarks())
                .build());

        auditLogService.log(currentUserProvider.username(), "DEPOSIT", "Account", locked.getId(),
                "Deposited " + request.getAmount() + " ref=" + txn.getTransactionRef());

        return toResponse(txn);
    }

    @Transactional
    public TransactionResponse withdraw(WithdrawalRequest request) {
        var existing = transactionRepository.findByTransactionRef(request.getIdempotencyKey());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Long accountId = accountRepository.findIdByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + request.getAccountNumber()));

        Account locked = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        assertOwnershipOrStaff(locked);
        assertActive(locked);
        enforceDailyLimit(locked, request.getAmount());

        BigDecimal newBalance = locked.getBalance().subtract(request.getAmount());
        if (newBalance.compareTo(locked.getMinBalance()) < 0) {
            throw new BusinessException("Insufficient funds: withdrawal would breach minimum balance of " + locked.getMinBalance());
        }

        Transaction txn = transactionRepository.save(Transaction.builder()
                .transactionRef(request.getIdempotencyKey())
                .type(TransactionType.WITHDRAWAL)
                .sourceAccount(locked)
                .amount(request.getAmount())
                .status(TransactionStatus.SUCCESS)
                .remarks(request.getRemarks())
                .initiatedByUsername(currentUserProvider.username())
                .build());

        locked.setBalance(newBalance);
        accountRepository.save(locked);

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transaction(txn).account(locked).entryType(LedgerEntryType.DEBIT)
                .amount(request.getAmount()).balanceAfter(newBalance).description(request.getRemarks())
                .build());

        auditLogService.log(currentUserProvider.username(), "WITHDRAWAL", "Account", locked.getId(),
                "Withdrew " + request.getAmount() + " ref=" + txn.getTransactionRef());

        return toResponse(txn);
    }

    @Transactional
    public TransactionResponse transfer(TransferRequest request) {
        if (request.getSourceAccountNumber().equals(request.getDestinationAccountNumber())) {
            throw new BusinessException("Source and destination accounts must differ");
        }

        var existing = transactionRepository.findByTransactionRef(request.getIdempotencyKey());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Long sourceId = accountRepository.findIdByAccountNumber(request.getSourceAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found: " + request.getSourceAccountNumber()));
        Long destinationId = accountRepository.findIdByAccountNumber(request.getDestinationAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found: " + request.getDestinationAccountNumber()));

        boolean sourceFirst = sourceId < destinationId;
        Account firstLock = accountRepository.findByIdForUpdate(sourceFirst ? sourceId : destinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        Account secondLock = accountRepository.findByIdForUpdate(sourceFirst ? destinationId : sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        Account lockedSource = sourceFirst ? firstLock : secondLock;
        Account lockedDestination = sourceFirst ? secondLock : firstLock;

        assertOwnershipOrStaff(lockedSource);
        assertActive(lockedSource);
        assertActive(lockedDestination);
        enforceDailyLimit(lockedSource, request.getAmount());

        BigDecimal newSourceBalance = lockedSource.getBalance().subtract(request.getAmount());
        if (newSourceBalance.compareTo(lockedSource.getMinBalance()) < 0) {
            throw new BusinessException("Insufficient funds: transfer would breach minimum balance of " + lockedSource.getMinBalance());
        }
        BigDecimal newDestBalance = lockedDestination.getBalance().add(request.getAmount());

        Transaction txn = transactionRepository.save(Transaction.builder()
                .transactionRef(request.getIdempotencyKey())
                .type(TransactionType.TRANSFER)
                .sourceAccount(lockedSource)
                .destinationAccount(lockedDestination)
                .amount(request.getAmount())
                .status(TransactionStatus.SUCCESS)
                .remarks(request.getRemarks())
                .initiatedByUsername(currentUserProvider.username())
                .build());

        lockedSource.setBalance(newSourceBalance);
        lockedDestination.setBalance(newDestBalance);
        accountRepository.save(lockedSource);
        accountRepository.save(lockedDestination);

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transaction(txn).account(lockedSource).entryType(LedgerEntryType.DEBIT)
                .amount(request.getAmount()).balanceAfter(newSourceBalance).description(request.getRemarks())
                .build());
        ledgerEntryRepository.save(LedgerEntry.builder()
                .transaction(txn).account(lockedDestination).entryType(LedgerEntryType.CREDIT)
                .amount(request.getAmount()).balanceAfter(newDestBalance).description(request.getRemarks())
                .build());

        auditLogService.log(currentUserProvider.username(), "TRANSFER", "Account", lockedSource.getId(),
                "Transferred " + request.getAmount() + " to " + lockedDestination.getAccountNumber() + " ref=" + txn.getTransactionRef());

        return toResponse(txn);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getByTransactionRef(String transactionRef) {
        Transaction txn = transactionRepository.findByTransactionRef(transactionRef)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionRef));
        return toResponse(txn);
    }

    private void assertActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException("Account " + account.getAccountNumber() + " is " + account.getStatus() + " and cannot transact");
        }
    }

    private void enforceDailyLimit(Account account, BigDecimal amount) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        BigDecimal alreadyDebitedToday = ledgerEntryRepository.sumDebitsSince(account.getId(), startOfDay);
        if (alreadyDebitedToday.add(amount).compareTo(account.getDailyTransferLimit()) > 0) {
            throw new BusinessException("Daily transfer limit of " + account.getDailyTransferLimit()
                    + " exceeded for account " + account.getAccountNumber());
        }
    }

    private void assertOwnershipOrStaff(Account account) {
        if (currentUserProvider.hasAnyRole("ADMIN", "TELLER")) {
            return;
        }
        if (!account.getCustomer().getUsername().equals(currentUserProvider.username())) {
            throw new BusinessException("You do not have access to this account");
        }
    }

    private TransactionResponse toResponse(Transaction txn) {
        return new TransactionResponse(
                txn.getTransactionRef(),
                txn.getType(),
                txn.getSourceAccount() != null ? txn.getSourceAccount().getAccountNumber() : null,
                txn.getDestinationAccount() != null ? txn.getDestinationAccount().getAccountNumber() : null,
                txn.getAmount(),
                txn.getStatus(),
                txn.getRemarks(),
                txn.getCreatedAt()
        );
    }
}
