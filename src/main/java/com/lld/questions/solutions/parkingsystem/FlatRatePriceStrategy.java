package com.lld.questions.solutions.parkingsystem;

import java.time.Duration;

public class FlatRatePriceStrategy implements PricingStrategy {
    private double priceRateMultiplier;

    public double getPriceRateMultiplier() {
        return priceRateMultiplier;
    }

    public void setPriceRateMultiplier(double priceRateMultiplier) {
        this.priceRateMultiplier = priceRateMultiplier;
    }

    @Override
    public double calculatePrice(Ticket ticket) {
        long totalMinutes = Duration.between(ticket.getEntryTime(), ticket.getExitTime()).toMillis() / 60000;
        return this.priceRateMultiplier * totalMinutes;
    }
}
