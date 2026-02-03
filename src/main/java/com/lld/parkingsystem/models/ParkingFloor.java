package com.lld.parkingsystem.models;

import java.util.ArrayList;
import java.util.List;

import com.lld.parkingsystem.enums.SpotType;
import com.lld.parkingsystem.models.vehicle.Vehicle;

public class ParkingFloor {
    private int floorNumber;
    private List<ParkingSpot> spots;

    public ParkingFloor(int floorNumber) {
        this.floorNumber = floorNumber;
        this.spots = new ArrayList<>();
    }

    public void addParkingSpot(ParkingSpot spot) {
        spots.add(spot);
    }

    public ParkingSpot findAvailableSpot(Vehicle vehicle) {
        for (ParkingSpot spot : spots) {
            if (spot.canFitVehicle(vehicle)) {
                return spot;
            }
        }
        return null;
    }

    public int getAvailableSpotCount(SpotType type) {
        int count = 0;
        for (ParkingSpot spot : spots) {
            if (spot.isAvailable() && spot.getSpotType() == type) {
                count++;
            }
        }
        return count;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public boolean hasAvailableSpot() {
        for (ParkingSpot spot : spots) {
            if (spot.isAvailable()) {
                return true;
            }
        }
        return false;
    }

    public List<ParkingSpot> getSpots() {
        return spots;
    }
}
