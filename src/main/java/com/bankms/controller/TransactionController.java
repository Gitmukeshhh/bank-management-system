package com.bankms.controller;

import com.bankms.dto.DepositRequest;
import com.bankms.dto.TransactionResponse;
import com.bankms.dto.TransferRequest;
import com.bankms.dto.WithdrawalRequest;
import com.bankms.service.FundTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final FundTransferService fundTransferService;

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(@Valid @RequestBody DepositRequest request) {
        return ResponseEntity.ok(fundTransferService.deposit(request));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(@Valid @RequestBody WithdrawalRequest request) {
        return ResponseEntity.ok(fundTransferService.withdraw(request));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest request) {
        return ResponseEntity.ok(fundTransferService.transfer(request));
    }

    @GetMapping("/{transactionRef}")
    public ResponseEntity<TransactionResponse> getByRef(@PathVariable String transactionRef) {
        return ResponseEntity.ok(fundTransferService.getByTransactionRef(transactionRef));
    }
}
