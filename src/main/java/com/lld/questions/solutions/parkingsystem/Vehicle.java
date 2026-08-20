package com.lld.questions.solutions.parkingsystem;

public class Vehicle {
    private String number;
    private String id;
    private VehicleType type;
    private ParkingSpot spot;

    public Vehicle(String number, String id, VehicleType type, ParkingSpot spot) {
        this.number = number;
        this.id = id;
        this.type = type;
        this.spot = spot;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public VehicleType getType() {
        return type;
    }

    public void setType(VehicleType type) {
        this.type = type;
    }

    public ParkingSpot getSpot() {
        return spot;
    }

    public void setSpot(ParkingSpot spot) {
        this.spot = spot;
    }
}
