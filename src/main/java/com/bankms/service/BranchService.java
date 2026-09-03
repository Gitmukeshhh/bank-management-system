package com.bankms.service;

import com.bankms.dto.BranchRequest;
import com.bankms.entity.Branch;
import com.bankms.exception.BusinessException;
import com.bankms.exception.ResourceNotFoundException;
import com.bankms.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    public Branch create(BranchRequest request) {
        branchRepository.findByIfscCode(request.getIfscCode()).ifPresent(b -> {
            throw new BusinessException("A branch with this IFSC code already exists");
        });

        Branch branch = Branch.builder()
                .branchName(request.getBranchName())
                .ifscCode(request.getIfscCode())
                .city(request.getCity())
                .build();
        return branchRepository.save(branch);
    }

    public List<Branch> getAll() {
        return branchRepository.findAll();
    }

    public Branch getById(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id " + id));
    }
}
