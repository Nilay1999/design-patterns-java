package com.lld.questions.solutions.vendingmachine;

public class CashPayment implements Payment {
    private final double amountToPay;

    public CashPayment(double amountToPay) {
        this.amountToPay = amountToPay;
    }

    public boolean pay(double amount) {
        return amount >= amountToPay;
    }

    public double getAmountToPay() {
        return amountToPay;
    }

}
