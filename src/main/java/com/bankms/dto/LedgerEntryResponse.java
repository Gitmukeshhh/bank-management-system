package com.bankms.dto;

import com.bankms.entity.LedgerEntryType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class LedgerEntryResponse {
    private LedgerEntryType entryType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String description;
    private String transactionRef;
    private LocalDateTime createdAt;
}
