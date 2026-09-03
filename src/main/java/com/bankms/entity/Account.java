package com.bankms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    /**
     * Cached running balance for fast reads. The ledger (see {@link LedgerEntry}) is the
     * source of truth; this column is only ever mutated inside the same transaction that
     * appends the corresponding ledger entry, under a pessimistic row lock.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    /** Can be negative for CURRENT accounts to represent an overdraft limit. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal minBalance;

    /** Annual interest rate percent; null/ignored for CURRENT accounts. */
    @Column(precision = 5, scale = 2)
    private BigDecimal interestRatePercent;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyTransferLimit;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** Optimistic-locking safety net for any update path that doesn't take the pessimistic lock. */
    @Version
    private Long version;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = AccountStatus.ACTIVE;
        }
    }
}
