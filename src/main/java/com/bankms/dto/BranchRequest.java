package com.bankms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BranchRequest {

    @NotBlank
    private String branchName;

    @NotBlank
    private String ifscCode;

    @NotBlank
    private String city;
}
