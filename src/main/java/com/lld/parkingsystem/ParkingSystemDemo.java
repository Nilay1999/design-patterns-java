package com.lld.parkingsystem;

import com.lld.parkingsystem.core.ParkingLot;
import com.lld.parkingsystem.enums.SpotType;
import com.lld.parkingsystem.interfaces.PricingStrategy;
import com.lld.parkingsystem.models.*;
import com.lld.parkingsystem.models.vehicle.*;
import com.lld.parkingsystem.service.*;

public class ParkingSystemDemo {
    public static void main(String[] args) {
        // Initialize parking lot with hourly pricing
        PricingStrategy pricingStrategy = new HourlyPricingStrategy();
        ParkingLot parkingLot = ParkingLot.getInstance("City Center Parking", pricingStrategy);

        // Setup Floor 1
        ParkingFloor floor1 = new ParkingFloor(1);
        floor1.addParkingSpot(new ParkingSpot(101, SpotType.BIKE));
        floor1.addParkingSpot(new ParkingSpot(102, SpotType.BIKE));
        floor1.addParkingSpot(new ParkingSpot(103, SpotType.COMPACT));
        floor1.addParkingSpot(new ParkingSpot(104, SpotType.COMPACT));
        floor1.addParkingSpot(new ParkingSpot(105, SpotType.LARGE));
        parkingLot.addFloor(floor1);

        // Setup Floor 2
        ParkingFloor floor2 = new ParkingFloor(2);
        floor2.addParkingSpot(new ParkingSpot(201, SpotType.COMPACT));
        floor2.addParkingSpot(new ParkingSpot(202, SpotType.COMPACT));
        floor2.addParkingSpot(new ParkingSpot(203, SpotType.LARGE));
        floor2.addParkingSpot(new ParkingSpot(204, SpotType.LARGE));
        parkingLot.addFloor(floor2);

        System.out.println("=== Parking System Initialized ===\n");

        // Display initial availability
        parkingLot.displayAvailability();

        // Test Case 1: Park different types of vehicles
        System.out.println("--- Test Case 1: Parking Vehicles ---");
        Vehicle car1 = new Car("ABC-123");
        Vehicle car2 = new Car("XYZ-789");
        Vehicle motorcycle1 = new Bike("BIKE-001");
        Vehicle truck1 = new Truck("TRUCK-555");
        Vehicle electricCar1 = new Bike("TESLA-001");

        ParkingTicket ticket1 = parkingLot.parkVehicle(car1);
        ParkingTicket ticket2 = parkingLot.parkVehicle(car2);
        ParkingTicket ticket3 = parkingLot.parkVehicle(motorcycle1);
        ParkingTicket ticket4 = parkingLot.parkVehicle(truck1);
        ParkingTicket ticket5 = parkingLot.parkVehicle(electricCar1);

        System.out.println();
        parkingLot.displayAvailability();

        // Test Case 2: Unpark vehicles and calculate fees
        System.out.println("--- Test Case 2: Unparking Vehicles ---");
        if (ticket1 != null) {
            // Simulate some time passing
            try {
                Thread.sleep(100); // Small delay for demo
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            double fee1 = parkingLot.unparkVehicle(ticket1.getTicketId());
            System.out.println("Parking fee for " + car1.getLicensePlate() + ": $" + fee1);
        }

        if (ticket3 != null) {
            double fee3 = parkingLot.unparkVehicle(ticket3.getTicketId());
            System.out.println("Parking fee for " + motorcycle1.getLicensePlate() + ": $" + fee3);
        }

        System.out.println();
        parkingLot.displayAvailability();

        // Test Case 3: Try to park when spot becomes available
        System.out.println("--- Test Case 3: Parking After Spot Freed ---");
        Vehicle car3 = new Car("NEW-999");
        ParkingTicket ticket6 = parkingLot.parkVehicle(car3);

        System.out.println();
        parkingLot.displayAvailability();

        // Test Case 4: Check if parking lot is full
        System.out.println("--- Test Case 4: Parking Lot Status ---");
        System.out.println("Is parking lot full? " + parkingLot.isFull());

        // Test Case 5: Invalid ticket
        System.out.println("\n--- Test Case 5: Invalid Ticket ---");
        parkingLot.unparkVehicle("INVALID-TICKET");

        System.out.println("\n=== Demo Complete ===");
    }
}