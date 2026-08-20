package com.lld.questions.solutions.parkingsystem;

import java.util.List;

public class Floor {
    private String id;
    private int totalSpots;
    private int occupiedSpots;
    private List<ParkingSpot> spots;

    public Floor(String id, int totalSpots, int occupiedSpots, List<ParkingSpot> spots) {
        this.id = id;
        this.totalSpots = totalSpots;
        this.occupiedSpots = occupiedSpots;
        this.spots = spots;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getTotalSpots() {
        return totalSpots;
    }

    public void setTotalSpots(int totalSpots) {
        this.totalSpots = totalSpots;
    }

    public int getOccupiedSpots() {
        return occupiedSpots;
    }

    public void setOccupiedSpots(int occupiedSpots) {
        this.occupiedSpots = occupiedSpots;
    }

    public List<ParkingSpot> getSpots() {
        return spots;
    }

    public void setSpots(List<ParkingSpot> spots) {
        this.spots = spots;
    }
}
