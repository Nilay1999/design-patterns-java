package com.lld.parkingsystem.interfaces;

import java.time.LocalDateTime;

import com.lld.parkingsystem.models.ParkingTicket;

public interface PricingStrategy {
    double calculateFee(ParkingTicket ticket, LocalDateTime exitTime);
}