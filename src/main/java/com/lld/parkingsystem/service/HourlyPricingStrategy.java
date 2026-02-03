package com.lld.parkingsystem.service;

import java.time.Duration;
import java.time.LocalDateTime;

import com.lld.parkingsystem.interfaces.PricingStrategy;
import com.lld.parkingsystem.models.ParkingTicket;

public class HourlyPricingStrategy implements PricingStrategy {
    private static final double HOURLY_RATE = 10.0;
    private static final double FIRST_HOUR_RATE = 5.0;

    @Override
    public double calculateFee(ParkingTicket ticket, LocalDateTime exitTime) {
        Duration duration = Duration.between(ticket.getEntryTime(), exitTime);
        long hours = duration.toHours();

        if (hours == 0) {
            return FIRST_HOUR_RATE;
        }

        return FIRST_HOUR_RATE + (hours * HOURLY_RATE);
    }
}