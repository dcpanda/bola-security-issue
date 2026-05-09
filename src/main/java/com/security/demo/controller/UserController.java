package com.security.demo.controller;

import com.security.demo.config.CustomUserDetails;
import com.security.demo.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private User getUserById(int id) {
        if (id == 1) return new User(1, "Alice", "alice@example.com", "ADMIN");
        if (id == 2) return new User(2, "Bob", "bob@example.com", "USER");
        if (id == 3) return new User(3, "Charlie", "charlie@example.com", "USER");
        return null;
    }

    @GetMapping("/bola-issue")
    public ResponseEntity<?> getUserBolaIssue(@RequestParam int userId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String loggedInUser = auth.getName();

        User user = getUserById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("requestedUser", user);
        response.put("loggedInAs", loggedInUser);
        response.put("warning", "BOLA Vulnerability - No authorization check!");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bola-fix")
    @PreAuthorize("#userId == authentication.principal.userId or hasRole('ADMIN')")
    public ResponseEntity<?> getUserBolaFix(@RequestParam int userId) {
        User user = getUserById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }
}
