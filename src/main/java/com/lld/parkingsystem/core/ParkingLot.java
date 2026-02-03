package com.lld.parkingsystem.core;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.lld.parkingsystem.enums.SpotType;
import com.lld.parkingsystem.interfaces.PricingStrategy;
import com.lld.parkingsystem.models.*;
import com.lld.parkingsystem.models.vehicle.Vehicle;

public class ParkingLot {
    private static ParkingLot instance;
    private String name;
    private List<ParkingFloor> floors;
    private Map<String, ParkingTicket> activeTickets;
    private PricingStrategy pricingStrategy;

    private ParkingLot(String name, PricingStrategy pricingStrategy) {
        this.name = name;
        this.floors = new ArrayList<>();
        this.activeTickets = new ConcurrentHashMap<>();
        this.pricingStrategy = pricingStrategy;
    }

    // Thread-safe singleton
    public static synchronized ParkingLot getInstance(String name, PricingStrategy pricingStrategy) {
        if (instance == null) {
            instance = new ParkingLot(name, pricingStrategy);
        }
        return instance;
    }

    public static ParkingLot getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ParkingLot not initialized");
        }
        return instance;
    }

    public void addFloor(ParkingFloor floor) {
        floors.add(floor);
    }

    public synchronized ParkingTicket parkVehicle(Vehicle vehicle) {
        for (ParkingFloor floor : floors) {
            ParkingSpot spot = floor.findAvailableSpot(vehicle);
            if (spot != null) {
                if (spot.parkVehicle(vehicle)) {
                    ParkingTicket ticket = new ParkingTicket(
                            vehicle.getLicensePlate(),
                            spot.getSpotNumber(),
                            floor.getFloorNumber());
                    activeTickets.put(ticket.getTicketId(), ticket);
                    System.out.println("Vehicle parked successfully: " + ticket);
                    return ticket;
                }
            }
        }
        System.out.println("No available spot for vehicle: " + vehicle.getLicensePlate());
        return null;
    }

    public synchronized double unparkVehicle(String ticketId) {
        ParkingTicket ticket = activeTickets.get(ticketId);
        if (ticket == null) {
            System.out.println("Invalid ticket ID");
            return -1;
        }

        LocalDateTime exitTime = LocalDateTime.now();
        double fee = pricingStrategy.calculateFee(ticket, exitTime);
        ticket.setExitTime(exitTime);
        ticket.setFee(fee);

        // Find and free the spot
        for (ParkingFloor floor : floors) {
            if (floor.getFloorNumber() == ticket.getFloorNumber()) {
                for (ParkingSpot spot : floor.getSpots()) {
                    if (spot.getSpotNumber() == ticket.getSpotNumber()) {
                        spot.removeVehicle();
                        break;
                    }
                }
            }
        }

        activeTickets.remove(ticketId);
        System.out.println("Vehicle unparked. Fee: $" + fee);
        return fee;
    }

    public void displayAvailability() {
        System.out.println("\n=== Parking Lot Availability ===");
        for (ParkingFloor floor : floors) {
            System.out.println("Floor " + floor.getFloorNumber() + ":");
            for (SpotType type : SpotType.values()) {
                int available = floor.getAvailableSpotCount(type);
                System.out.println("  " + type + ": " + available + " available");
            }
        }
        System.out.println("================================\n");
    }

    public boolean isFull() {
        for (ParkingFloor floor : floors) {
            if (floor.hasAvailableSpot()) {
                return false;
            }
        }
        return true;
    }

    public String getName() {
        return name;
    }
}
