package com.security.demo.config;

import com.security.demo.model.Account;
import com.security.demo.repository.AccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(AccountRepository accountRepository) {
        return args -> {
            accountRepository.save(new Account("Savings Account", "ACC-001", "user1", 5000.00));
            accountRepository.save(new Account("Checking Account", "ACC-002", "user1", 1500.00));
            accountRepository.save(new Account("Savings Account", "ACC-003", "user2", 10000.00));
            accountRepository.save(new Account("Investment Account", "ACC-004", "user2", 25000.00));
        };
    }
}
