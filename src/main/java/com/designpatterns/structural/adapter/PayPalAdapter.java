package com.designpatterns.structural.adapter;

public class PayPalAdapter implements PaymentGateway{
    private final PayPalPayment payPal;

    public PayPalAdapter(PayPalPayment payPal) {
        this.payPal = payPal;
    }

    @Override
    public void pay(int amount) {
        int amountWithCharges = 10 * amount;
        payPal.makePayment(amountWithCharges);
    }

    public static void main(String[] args) {
        PaymentGateway payment = new PayPalAdapter(new PayPalPayment());
        payment.pay(10); // Pays $10 using PayPal via the adapter
    }
}
