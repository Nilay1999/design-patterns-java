package com.lld.questions.solutions.atmmachine;

import java.util.List;

public class Bank {
    public String bankId;
    public List<Account> accounts;

    public Account getAccount(String accountNumber) {
        return this.accounts.stream().filter((acc) -> acc.getAccountNumber().equals(accountNumber)).findFirst()
                .orElse(null);
    }

    public boolean authenticate(Card card, int pinNumber) {
        if (card.verifyPinNumber(pinNumber)) {
            return true;
        }
        return false;
    }

    public Bank(String bankId, List<Account> accounts) {
        this.bankId = bankId;
        this.accounts = accounts;
    }

    public String getBankId() {
        return bankId;
    }

    public void setBankId(String bankId) {
        this.bankId = bankId;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts;
    }
}
