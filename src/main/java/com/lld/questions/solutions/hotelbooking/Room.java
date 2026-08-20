package com.lld.questions.solutions.hotelbooking;

import java.time.Instant;
import java.util.List;

public class Room {
    private String roomId;
    private RoomType roomType;
    private RoomStatus status;
    private String description;
    private Hotel hotel;
    private int capacity;
    private double price;
    private List<Booking> bookings;

    public Room(String roomId, RoomType roomType, RoomStatus status, String description, Hotel hotel, int capacity,
            double price, List<Booking> bookings) {
        this.roomId = roomId;
        this.roomType = roomType;
        this.status = status;
        this.description = description;
        this.hotel = hotel;
        this.capacity = capacity;
        this.price = price;
        this.bookings = bookings;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public void setRoomType(RoomType roomType) {
        this.roomType = roomType;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Hotel getHotel() {
        return hotel;
    }

    public void setHotel(Hotel hotel) {
        this.hotel = hotel;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public List<Booking> getBookings() {
        return bookings;
    }

    public void setBookings(List<Booking> bookings) {
        this.bookings = bookings;
    }

    public boolean isAvailable(Instant startDate, Instant endDate) {
        Instant now = Instant.now();
        for (Booking booking : bookings) {
            // skip bookings that don't hold the room: cancelled, or an expired hold
            boolean active = booking.getStatus() == BookingStatus.BOOKED
                    || (booking.getStatus() == BookingStatus.HELD
                            && booking.getHoldExpiresAt().isAfter(now));
            if (!active) {
                continue;
            }
            if (startDate.isBefore(booking.getEndDate()) && booking.getStartDate().isBefore(endDate)) {
                return false;
            }
        }
        return true;
    }
}
