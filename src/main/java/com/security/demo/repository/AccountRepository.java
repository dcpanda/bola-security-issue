package com.security.demo.repository;

import com.security.demo.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByIdAndOwnerUsername(Long id, String ownerUsername);
}
