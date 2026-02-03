package com.lld.parkingsystem.models;

import com.lld.parkingsystem.enums.SpotType;
import com.lld.parkingsystem.enums.VehicleType;
import com.lld.parkingsystem.models.vehicle.Vehicle;

public class ParkingSpot {
    private int spotNumber;
    private SpotType spotType;
    private Vehicle currentVehicle;
    private boolean isAvailable;

    public ParkingSpot(int spotNumber, SpotType spotType) {
        this.spotNumber = spotNumber;
        this.spotType = spotType;
        this.isAvailable = true;
    }

    public boolean canFitVehicle(Vehicle vehicle) {
        if (!isAvailable) {
            return false;
        }

        switch (spotType) {
            case COMPACT:
                return vehicle.getType() == VehicleType.BIKE ||
                        vehicle.getType() == VehicleType.CAR;
            case LARGE:
                return vehicle.getType() == VehicleType.TRUCK ||
                        vehicle.getType() == VehicleType.CAR ||
                        vehicle.getType() == VehicleType.BIKE;
            case BIKE:
                return vehicle.getType() == VehicleType.BIKE;
            default:
                return false;
        }
    }

    public boolean parkVehicle(Vehicle vehicle) {
        if (canFitVehicle(vehicle)) {
            this.currentVehicle = vehicle;
            this.isAvailable = false;
            return true;
        }
        return false;
    }

    public void removeVehicle() {
        this.currentVehicle = null;
        this.isAvailable = true;
    }

    public int getSpotNumber() {
        return spotNumber;
    }

    public SpotType getSpotType() {
        return spotType;
    }

    public Vehicle getCurrentVehicle() {
        return currentVehicle;
    }

    public boolean isAvailable() {
        return isAvailable;
    }
}