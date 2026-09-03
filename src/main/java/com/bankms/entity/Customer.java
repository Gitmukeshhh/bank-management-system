package com.bankms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Links this customer profile to their login account for self-service ownership checks. */
    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false)
    private LocalDate dob;

    @Column(nullable = false, unique = true, length = 10)
    private String panNumber;

    @Column(length = 10)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
