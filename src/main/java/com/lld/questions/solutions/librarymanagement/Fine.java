package com.lld.questions.solutions.librarymanagement;

public class Fine {
    private int id;
    private Holding holding;
    private FineStrategy fineStrategy;
    private double amount;

    public Fine(int id, Holding holding, FineStrategy fineStrategy) {
        this.id = id;
        this.holding = holding;
        this.fineStrategy = fineStrategy;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Holding getHolding() {
        return holding;
    }

    public void setHolding(Holding holding) {
        this.holding = holding;
    }

    public double calculateFine() {
        this.amount = fineStrategy.calculate(holding);
        return amount;
    }

    public double getAmount() {
        return amount;
    }
}
