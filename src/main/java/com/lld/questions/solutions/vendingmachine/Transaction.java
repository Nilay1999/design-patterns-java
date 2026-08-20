package com.lld.questions.solutions.vendingmachine;

public class Transaction {
    private Slot slot;
    private double amountDue;
    private double amountPaid;
    private TransactionStatus status;

    public Transaction(Slot slot, double amountDue) {
        this.slot = slot;
        this.amountDue = amountDue;
        this.amountPaid = 0;
        this.status = TransactionStatus.INITIATED;
    }

    // Records a payment. Accumulates across multiple inserts; marks PAID once
    // the bill is covered, otherwise PAYMENT_PENDING. Change is amountPaid -
    // amountDue.
    public void applyPayment(double amount) {
        this.amountPaid += amount;
        if (this.amountPaid >= this.amountDue) {
            this.status = TransactionStatus.PAID;
        } else {
            this.status = TransactionStatus.PAYMENT_PENDING;
        }
    }

    // Amount to return to the customer once PAID (0 while still pending).
    public double getChange() {
        return Math.max(0, this.amountPaid - this.amountDue);
    }

    public Slot getSlot() {
        return slot;
    }

    public void setSlot(Slot slot) {
        this.slot = slot;
    }

    public double getAmountDue() {
        return amountDue;
    }

    public double getAmountPaid() {
        return amountPaid;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

}
