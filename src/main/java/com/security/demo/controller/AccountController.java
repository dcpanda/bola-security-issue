package com.security.demo.controller;

import com.security.demo.model.Account;
import com.security.demo.repository.AccountRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountRepository accountRepository;

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping("/bola-issue/{id}")
    public ResponseEntity<?> getAccountBolaIssue(@PathVariable Long id, Principal principal) {
        return accountRepository.findById(id)
            .map(account -> {
                Map<String, Object> response = new java.util.HashMap<>();
                response.put("account", account);
                response.put("loggedInAs", principal.getName());
                response.put("warning", "BOLA Vulnerability - No authorization check!");
                return ResponseEntity.ok(response);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/bola-fix/{id}")
    public ResponseEntity<?> getAccountBolaFix(@PathVariable Long id, Principal principal) {
        return accountRepository.findByIdAndOwnerUsername(id, principal.getName())
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<?> getMyAccounts(Principal principal) {
        return ResponseEntity.ok(accountRepository.findAll());
    }
}
