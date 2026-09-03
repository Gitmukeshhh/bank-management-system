package com.bankms.dto;

import com.bankms.entity.TransactionStatus;
import com.bankms.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class TransactionResponse {
    private String transactionRef;
    private TransactionType type;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private BigDecimal amount;
    private TransactionStatus status;
    private String remarks;
    private LocalDateTime createdAt;
}
