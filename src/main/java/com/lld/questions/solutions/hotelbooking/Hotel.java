package com.lld.questions.solutions.hotelbooking;

import java.time.Instant;
import java.util.List;

public class Hotel {
    private String hotelId;
    private Location location;
    private List<Room> rooms;

    public Hotel(String hotelId, Location location) {
        this.hotelId = hotelId;
        this.location = location;
    }

    public List<Room> searchRooms(RoomType type, Instant startDate, Instant endDate) {
        return this.rooms.stream().filter((r) -> {
            if (r.isAvailable(startDate, endDate) && r.getRoomType() == type) {
                return true;
            }
            return false;
        }).toList();
    }

    public String getHotelId() {
        return hotelId;
    }

    public void setHotelId(String hotelId) {
        this.hotelId = hotelId;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }
}
