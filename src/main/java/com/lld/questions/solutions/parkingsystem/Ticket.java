package com.lld.questions.solutions.parkingsystem;

import java.time.Instant;

public class Ticket {
    private String id;
    private Vehicle vehicle;
    private ParkingSpot parkedSpot;
    private Instant entryTime;
    private Instant exitTime;
    private TicketStatus status;
    private PricingStrategy pricingStrategy;

    public Ticket(Vehicle vehicle, ParkingSpot parkedSpot, PricingStrategy pricingStrategy) {
        this.vehicle = vehicle;
        this.parkedSpot = parkedSpot;
        this.entryTime = Instant.now();
        this.status = TicketStatus.ACTIVE;
        this.pricingStrategy = pricingStrategy;
    }

    public double calculateTicketFee() {
        return this.pricingStrategy.calculatePrice(this);
    }

    public boolean isActive() {
        return status == TicketStatus.ACTIVE;
    }

    public double closeTicket() {
        if (!isActive()) {
            throw new IllegalStateException("Ticket already closed");
        }
        this.exitTime = Instant.now();
        double fee = calculateTicketFee();
        this.status = TicketStatus.PAID;
        return fee;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public ParkingSpot getParkedSpot() {
        return parkedSpot;
    }

    public void setParkedSpot(ParkingSpot parkedSpot) {
        this.parkedSpot = parkedSpot;
    }

    public Instant getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(Instant entryTime) {
        this.entryTime = entryTime;
    }

    public Instant getExitTime() {
        return exitTime;
    }

    public void setExitTime(Instant exitTime) {
        this.exitTime = exitTime;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public PricingStrategy getPricingStrategy() {
        return pricingStrategy;
    }

    public void setPricingStrategy(PricingStrategy pricingStrategy) {
        this.pricingStrategy = pricingStrategy;
    }
};
