package com.lld.parkingsystem.service;

import java.time.LocalDateTime;

import com.lld.parkingsystem.interfaces.PricingStrategy;
import com.lld.parkingsystem.models.ParkingTicket;

class FlatRatePricingStrategy implements PricingStrategy {
    private static final double FLAT_RATE = 20.0;

    @Override
    public double calculateFee(ParkingTicket ticket, LocalDateTime exitTime) {
        return FLAT_RATE;
    }
}
