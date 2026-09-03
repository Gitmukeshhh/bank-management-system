package com.bankms.service;

import com.bankms.entity.Account;
import com.bankms.entity.AccountStatus;
import com.bankms.entity.AccountType;
import com.bankms.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterestAccrualService {

    private final AccountRepository accountRepository;
    private final InterestPostingService interestPostingService;

    @Scheduled(cron = "${bank.interest.cron}")
    public void accrueDailyInterest() {
        List<Account> savingsAccounts = accountRepository.findByStatusAndAccountType(AccountStatus.ACTIVE, AccountType.SAVINGS);
        for (Account account : savingsAccounts) {
            try {
                interestPostingService.accrueForAccount(account.getId());
            } catch (Exception e) {
                log.error("Interest accrual failed for account {}: {}", account.getAccountNumber(), e.getMessage());
            }
        }
    }
}
