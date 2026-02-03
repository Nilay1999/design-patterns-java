package com.lld.parkingsystem.interfaces;

import java.util.List;

import com.lld.parkingsystem.models.ParkingSpot;

public interface SlotService {
    public ParkingSpot getAvailableSlot(List<ParkingSpot> currentSlots);

    public Boolean occupySlot(ParkingSpot slot);

    public Boolean vacate(ParkingSpot slot);
}
