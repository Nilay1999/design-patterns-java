package com.lld.questions.solutions.hotelbooking;

import java.util.UUID;

public class Payment {
    private String paymentId;
    private String bookingId;
    private double amount;
    private PaymentType type;
    private PaymentStatus paymentStatus;

    public Payment(String bookingId, double amount, PaymentType type) {
        this.paymentId = UUID.randomUUID().toString();
        this.bookingId = bookingId;
        this.amount = amount;
        this.type = type;
        this.paymentStatus = PaymentStatus.PENDING;
    }

    public void makePayment() {
    }

    public void cancelPayment() {
    }

    public void refundPayment() {
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public PaymentType getType() {
        return type;
    }

    public void setType(PaymentType type) {
        this.type = type;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
}
