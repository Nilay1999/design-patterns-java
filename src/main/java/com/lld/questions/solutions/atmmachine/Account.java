package com.lld.questions.solutions.atmmachine;

import java.util.List;

public class Account {
    private String accountNumber;
    private int balance;
    private List<Card> cards;

    public Account(String accountNumber, int amount, List<Card> cards) {
        this.accountNumber = accountNumber;
        this.balance = amount;
        this.cards = cards;
    }

    public void withdraw(int amount) {
        if (hasSufficientBalance(amount)) {
            this.balance -= amount;
        } else {
            throw new RuntimeException("Not sufficient funds");
        }
    }

    public void deposite(int amount) {
        this.balance += amount;
    }

    public boolean hasSufficientBalance(int amount) {
        return this.balance >= amount;
    }

    public List<Card> getCards() {
        return cards;
    }

    public void setCards(List<Card> cards) {
        this.cards = cards;
    }

    public Account(String accountNumber, int amount) {
        this.accountNumber = accountNumber;
        this.balance = amount;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public int getBalance() {
        return balance;
    }

    public void setBalance(int amount) {
        this.balance = amount;
    }
}
