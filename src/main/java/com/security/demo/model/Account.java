package com.security.demo.model;

import jakarta.persistence.*;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String accountName;
    private String accountNumber;
    private String ownerUsername;
    private double balance;

    public Account() {}

    public Account(String accountName, String accountNumber, String ownerUsername, double balance) {
        this.accountName = accountName;
        this.accountNumber = accountNumber;
        this.ownerUsername = ownerUsername;
        this.balance = balance;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getOwnerUsername() { return ownerUsername; }
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
}
