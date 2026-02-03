package com.lld.parkingsystem.models;

import java.time.LocalDateTime;

public class ParkingTicket {
    private String ticketId;
    private String licensePlate;
    private int spotNumber;
    private int floorNumber;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private double fee;

    public ParkingTicket(String licensePlate, int spotNumber, int floorNumber) {
        this.ticketId = generateTicketId();
        this.licensePlate = licensePlate;
        this.spotNumber = spotNumber;
        this.floorNumber = floorNumber;
        this.entryTime = LocalDateTime.now();
    }

    private String generateTicketId() {
        return "TKT-" + System.currentTimeMillis();
    }

    public void setExitTime(LocalDateTime exitTime) {
        this.exitTime = exitTime;
    }

    public void setFee(double fee) {
        this.fee = fee;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public int getSpotNumber() {
        return spotNumber;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public LocalDateTime getExitTime() {
        return exitTime;
    }

    public double getFee() {
        return fee;
    }

    @Override
    public String toString() {
        return "ParkingTicket{" +
                "ticketId='" + ticketId + '\'' +
                ", licensePlate='" + licensePlate + '\'' +
                ", floor=" + floorNumber +
                ", spot=" + spotNumber +
                ", entryTime=" + entryTime +
                '}';
    }
}
