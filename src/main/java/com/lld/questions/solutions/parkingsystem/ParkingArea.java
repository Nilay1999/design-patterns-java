package com.lld.questions.solutions.parkingsystem;

import java.util.List;
import java.util.UUID;

public class ParkingArea {
    private String id;
    private List<Floor> floor;
    private PricingStrategy pricingStrategy;

    public ParkingArea(String id, List<Floor> floor, PricingStrategy pricingStrategy) {
        this.id = id;
        this.floor = floor;
        this.pricingStrategy = pricingStrategy;
    }

    public ParkingSpot findAvailableSpot(VehicleType type) {
        for (SpotType spotType : SpotMatcher.compatibleSpots(type)) {
            for (Floor floor : this.floor) {
                for (ParkingSpot spot : floor.getSpots()) {
                    if (spot.getType() == spotType && !spot.isOccupied()) {
                        return spot;
                    }
                }
            }
        }
        return null;
    }

    public Ticket parkVehicle(Vehicle vehicle) {
        ParkingSpot spot = findAvailableSpot(vehicle.getType());
        if (spot == null) {
            return null;
        }

        // spot just records occupancy — that's all it's responsible for
        spot.setVehicle(vehicle);

        // facade assembles the ticket because only it knows vehicle + spot + strategy
        Ticket ticket = new Ticket(vehicle, spot, this.pricingStrategy);
        ticket.setId(UUID.randomUUID().toString());
        return ticket;
    }

    public double unparkVehicle(Ticket ticket) {
        double fee = ticket.closeTicket();
        ticket.getParkedSpot().setVehicle(null); // occupancy is derived from vehicle
        return fee;
    }

    public int getTotalAvailableSpots() {
        int total = 0;
        for (Floor floor : this.floor) {
            for (ParkingSpot spot : floor.getSpots()) {
                if (!spot.isOccupied()) {
                    total++;
                }
            }
        }
        return total;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<Floor> getFloor() {
        return floor;
    }

    public void setFloor(List<Floor> floor) {
        this.floor = floor;
    }
};