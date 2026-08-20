package com.lld.questions.solutions.parkingsystem;

public class ParkingSpot {
    private String id;
    private SpotType type;
    private Vehicle vehicle;
    private Floor floor;

    public ParkingSpot(String id, SpotType type, Vehicle vehicle, Floor floor) {
        this.id = id;
        this.type = type;
        this.vehicle = vehicle;
        this.floor = floor;
    }

    public boolean isOccupied() {
        return this.vehicle != null;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public SpotType getType() {
        return type;
    }

    public void setType(SpotType type) {
        this.type = type;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public Floor getFloor() {
        return floor;
    }

    public void setFloor(Floor floor) {
        this.floor = floor;
    }
}
