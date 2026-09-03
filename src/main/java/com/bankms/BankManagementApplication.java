package com.bankms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BankManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(BankManagementApplication.class, args);
    }
}
