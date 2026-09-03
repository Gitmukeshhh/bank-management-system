package com.bankms.controller;

import com.bankms.dto.AccountOpenRequest;
import com.bankms.dto.AccountResponse;
import com.bankms.dto.LedgerEntryResponse;
import com.bankms.entity.AccountStatus;
import com.bankms.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER')")
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody AccountOpenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.openAccount(request));
    }

    @GetMapping("/me")
    public ResponseEntity<List<AccountResponse>> myAccounts() {
        return ResponseEntity.ok(accountService.getMyAccounts());
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> getByAccountNumber(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getByAccountNumber(accountNumber));
    }

    @GetMapping("/{accountNumber}/statement")
    public ResponseEntity<Page<LedgerEntryResponse>> statement(@PathVariable String accountNumber,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(accountService.getStatement(accountNumber, PageRequest.of(page, size)));
    }

    @PatchMapping("/{accountNumber}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountResponse> updateStatus(@PathVariable String accountNumber,
                                                         @RequestParam AccountStatus status) {
        return ResponseEntity.ok(accountService.updateStatus(accountNumber, status));
    }
}
