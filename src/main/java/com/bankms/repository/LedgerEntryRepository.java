package com.bankms.repository;

import com.bankms.entity.LedgerEntry;
import com.bankms.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Page<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    @Query("select coalesce(sum(l.amount), 0) from LedgerEntry l " +
           "where l.account.id = :accountId and l.entryType = 'DEBIT' and l.createdAt >= :since")
    BigDecimal sumDebitsSince(@Param("accountId") Long accountId, @Param("since") LocalDateTime since);

    @Query("select count(l) > 0 from LedgerEntry l " +
           "where l.account.id = :accountId and l.transaction.type = :type and l.createdAt >= :since")
    boolean existsByAccountAndTransactionTypeSince(@Param("accountId") Long accountId,
                                                    @Param("type") TransactionType type,
                                                    @Param("since") LocalDateTime since);
}
