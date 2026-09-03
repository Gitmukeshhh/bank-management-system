package com.bankms.dto;

import com.bankms.entity.AccountType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountOpenRequest {

    @NotBlank
    private String customerUsername;

    @NotNull
    private Long branchId;

    @NotNull
    private AccountType accountType;

    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal initialDeposit;

    /** Negative allowed for CURRENT accounts to represent an overdraft limit. */
    @NotNull
    private BigDecimal minBalance;

    private BigDecimal interestRatePercent;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal dailyTransferLimit;
}
