package com.lld.questions.solutions.parkingsystem;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // --- Build the lot: one floor with 3 spots of different sizes ---
        List<ParkingSpot> spots = new ArrayList<>();
        Floor floor = new Floor("F1", 3, 0, spots);
        spots.add(new ParkingSpot("S1", SpotType.SMALL, null, floor));
        spots.add(new ParkingSpot("S2", SpotType.MEDIUM, null, floor));
        spots.add(new ParkingSpot("S3", SpotType.LARGE, null, floor));

        // Flat rate: 1.0 currency unit per minute
        FlatRatePriceStrategy pricing = new FlatRatePriceStrategy();
        pricing.setPriceRateMultiplier(1.0);

        ParkingArea lot = new ParkingArea("LOT-1", List.of(floor), pricing);

        System.out.println("Available spots at start: " + lot.getTotalAvailableSpots());

        // --- Park a LIGHT vehicle (should take the SMALL spot first) ---
        Vehicle bike = new Vehicle("KA-01-1234", "V1", VehicleType.LIGHT, null);
        Ticket bikeTicket = lot.parkVehicle(bike);
        System.out.println("Parked LIGHT vehicle at spot: "
                + bikeTicket.getParkedSpot().getId() + " (" + bikeTicket.getParkedSpot().getType() + ")");

        // --- Park a HEAVY vehicle (only LARGE is compatible) ---
        Vehicle truck = new Vehicle("KA-02-9999", "V2", VehicleType.HEAVY, null);
        Ticket truckTicket = lot.parkVehicle(truck);
        System.out.println("Parked HEAVY vehicle at spot: "
                + truckTicket.getParkedSpot().getId() + " (" + truckTicket.getParkedSpot().getType() + ")");

        System.out.println("Available spots after 2 parked: " + lot.getTotalAvailableSpots());

        // --- Simulate a 90-minute stay by backdating the entry time ---
        bikeTicket.setEntryTime(Instant.now().minus(90, ChronoUnit.MINUTES));

        // --- Unpark the bike: fee = rate * minutes ---
        double fee = lot.unparkVehicle(bikeTicket);
        System.out.println("Bike unparked. Fee = " + fee + ", status = " + bikeTicket.getStatus());
        System.out.println("Available spots after bike leaves: " + lot.getTotalAvailableSpots());

        // --- Closing an already-closed ticket should fail loudly ---
        try {
            lot.unparkVehicle(bikeTicket);
        } catch (IllegalStateException e) {
            System.out.println("Re-closing ticket rejected: " + e.getMessage());
        }

        // --- Try to park a second HEAVY vehicle: no LARGE spot left -> null ---
        Vehicle truck2 = new Vehicle("KA-03-0000", "V3", VehicleType.HEAVY, null);
        Ticket truck2Ticket = lot.parkVehicle(truck2);
        System.out.println("Second HEAVY vehicle ticket: "
                + (truck2Ticket == null ? "REJECTED (lot full for type)" : truck2Ticket.getId()));
    }
}
