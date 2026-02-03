package com.lld.parkingsystem.models.vehicle;

import com.lld.parkingsystem.enums.VehicleType;

public class Car extends Vehicle {
    public Car(String licensePlate) {
        super(licensePlate, VehicleType.CAR);
    }
}
