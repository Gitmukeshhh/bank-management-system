package com.bankms.config;

import com.bankms.entity.Branch;
import com.bankms.entity.Role;
import com.bankms.entity.User;
import com.bankms.repository.BranchRepository;
import com.bankms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername("admin").isEmpty()) {
            userRepository.save(User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .role(Role.ADMIN)
                    .build());
        }

        if (userRepository.findByUsername("teller1").isEmpty()) {
            userRepository.save(User.builder()
                    .username("teller1")
                    .password(passwordEncoder.encode("teller123"))
                    .role(Role.TELLER)
                    .build());
        }

        if (branchRepository.findByIfscCode("BANK0000001").isEmpty()) {
            branchRepository.save(Branch.builder()
                    .branchName("Head Office")
                    .ifscCode("BANK0000001")
                    .city("Mumbai")
                    .build());
        }
    }
}
