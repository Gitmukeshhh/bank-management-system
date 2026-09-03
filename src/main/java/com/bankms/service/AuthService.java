package com.bankms.service;

import com.bankms.dto.AuthRequest;
import com.bankms.dto.AuthResponse;
import com.bankms.dto.CustomerRegisterRequest;
import com.bankms.entity.Customer;
import com.bankms.entity.Role;
import com.bankms.entity.User;
import com.bankms.exception.BusinessException;
import com.bankms.repository.CustomerRepository;
import com.bankms.repository.UserRepository;
import com.bankms.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public void registerCustomer(CustomerRegisterRequest request) {
        userRepository.findByUsername(request.getUsername()).ifPresent(u -> {
            throw new BusinessException("Username already taken");
        });
        customerRepository.findByPanNumber(request.getPanNumber()).ifPresent(c -> {
            throw new BusinessException("A customer with this PAN already exists");
        });

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .build();
        userRepository.save(user);

        Customer customer = Customer.builder()
                .username(request.getUsername())
                .fullName(request.getFullName())
                .dob(request.getDob())
                .panNumber(request.getPanNumber())
                .phone(request.getPhone())
                .email(request.getEmail())
                .build();
        customerRepository.save(customer);
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }
}
