package com.bankms.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "branches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String branchName;

    @Column(nullable = false, unique = true, length = 11)
    private String ifscCode;

    @Column(nullable = false, length = 50)
    private String city;
}
