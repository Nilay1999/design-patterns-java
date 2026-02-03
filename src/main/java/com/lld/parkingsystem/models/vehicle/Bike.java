package com.lld.parkingsystem.models.vehicle;

import com.lld.parkingsystem.enums.VehicleType;

public class Bike extends Vehicle {
    public Bike(String licensePlate) {
        super(licensePlate, VehicleType.BIKE);
    }
}
