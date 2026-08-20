package com.lld.questions.solutions.vendingmachine;

public interface Payment {
    // Returns true if the payment succeeds (cash covers the bill / charge goes
    // through).
    boolean pay(double amount);
}
