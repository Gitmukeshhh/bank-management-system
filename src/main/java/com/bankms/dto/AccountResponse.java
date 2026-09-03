package com.bankms.dto;

import com.bankms.entity.AccountStatus;
import com.bankms.entity.AccountType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class AccountResponse {
    private Long id;
    private String accountNumber;
    private AccountType accountType;
    private AccountStatus status;
    private BigDecimal balance;
    private BigDecimal minBalance;
    private BigDecimal interestRatePercent;
    private BigDecimal dailyTransferLimit;
    private String branchName;
    private String customerUsername;
    private String customerFullName;
    private LocalDateTime createdAt;
}
