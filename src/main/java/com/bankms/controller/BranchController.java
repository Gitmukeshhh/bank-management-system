package com.bankms.controller;

import com.bankms.dto.BranchRequest;
import com.bankms.entity.Branch;
import com.bankms.service.BranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Branch> create(@Valid @RequestBody BranchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(branchService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<Branch>> getAll() {
        return ResponseEntity.ok(branchService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Branch> getById(@PathVariable Long id) {
        return ResponseEntity.ok(branchService.getById(id));
    }
}
