package com.lld.questions.solutions.atmmachine;

public class Card {
    public String cardNumber;
    public int pinNumber;
    public Account account;

    public Card(String cardNumber, int pinNumber, Account account) {
        this.cardNumber = cardNumber;
        this.pinNumber = pinNumber;
        this.account = account;
    }

    public boolean verifyPinNumber(int pin) {
        return this.pinNumber == pin;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public int getPinNumber() {
        return pinNumber;
    }

    public void setPinNumber(int pinNumber) {
        this.pinNumber = pinNumber;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }
}
