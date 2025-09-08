package com.designpatterns.structural.adapter;

public class PayPalPayment {
    public void makePayment(int amountInCents) {
        System.out.println("Paid " + amountInCents + " cents via PayPal.");
    }
}
