package com.bankms.repository;

import com.bankms.entity.Account;
import com.bankms.entity.AccountStatus;
import com.bankms.entity.AccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Returns only the id, without loading a managed Account into the persistence context.
     * Used to resolve an account number to an id before taking a pessimistic lock on it —
     * loading the full entity unlocked first would poison the persistence context with a
     * stale @Version snapshot and make the subsequent lock-upgrade fail spuriously.
     */
    @Query("select a.id from Account a where a.accountNumber = :accountNumber")
    Optional<Long> findIdByAccountNumber(@Param("accountNumber") String accountNumber);

    /**
     * Row-level pessimistic write lock. Must be called from within a @Transactional method;
     * the lock is held until that transaction commits/rolls back, serializing concurrent
     * mutations of the same account (see FundTransferService for the deadlock-safe lock order).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    List<Account> findByStatusAndAccountType(AccountStatus status, AccountType accountType);

    List<Account> findByCustomerUsername(String username);
}
