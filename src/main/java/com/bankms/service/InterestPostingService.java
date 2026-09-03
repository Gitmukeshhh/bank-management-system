package com.bankms.service;

import com.bankms.entity.*;
import com.bankms.repository.AccountRepository;
import com.bankms.repository.LedgerEntryRepository;
import com.bankms.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Posts interest for a single account inside its own transaction, under the same
 * pessimistic lock used by fund transfers, so an interest run can never race with a
 * concurrent withdrawal. Kept as a separate bean (not a private method on the scheduler)
 * so Spring's transactional proxy actually applies — a same-class call would bypass it.
 */
@Service
@RequiredArgsConstructor
public class InterestPostingService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditLogService auditLogService;

    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);

    @Transactional
    public void accrueForAccount(Long accountId) {
        Account account = accountRepository.findByIdForUpdate(accountId).orElseThrow();

        if (account.getInterestRatePercent() == null || account.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        if (ledgerEntryRepository.existsByAccountAndTransactionTypeSince(accountId, TransactionType.INTEREST_CREDIT, startOfDay)) {
            return; // already credited today - guards against duplicate/overlapping job runs
        }

        BigDecimal dailyInterest = account.getBalance()
                .multiply(account.getInterestRatePercent())
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .divide(DAYS_IN_YEAR, 2, RoundingMode.HALF_UP);

        if (dailyInterest.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Transaction txn = Transaction.builder()
                .transactionRef("INTEREST-" + account.getAccountNumber() + "-" + LocalDate.now())
                .type(TransactionType.INTEREST_CREDIT)
                .destinationAccount(account)
                .amount(dailyInterest)
                .status(TransactionStatus.SUCCESS)
                .remarks("Daily interest accrual")
                .initiatedByUsername("SYSTEM")
                .build();
        txn = transactionRepository.save(txn);

        BigDecimal newBalance = account.getBalance().add(dailyInterest);
        account.setBalance(newBalance);
        accountRepository.save(account);

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transaction(txn).account(account).entryType(LedgerEntryType.CREDIT)
                .amount(dailyInterest).balanceAfter(newBalance).description("Daily interest accrual")
                .build());

        auditLogService.log("SYSTEM", "INTEREST_CREDIT", "Account", account.getId(),
                "Credited interest " + dailyInterest);
    }
}
