package com.lld.moviebooking.repository;

import com.lld.moviebooking.models.Booking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BookingRepository {
    private final Map<String, Booking> bookings = new HashMap<>();

    public void add(Booking booking) {
        bookings.put(booking.getId(), booking);
    }

    public Booking get(String id) {
        return bookings.get(id);
    }

    public List<Booking> getByUser(String userId) {
        List<Booking> result = new ArrayList<>();
        for (Booking b : bookings.values()) {
            if (b.getBookedBy() != null && b.getBookedBy().getId().equals(userId)) {
                result.add(b);
            }
        }
        return result;
    }
}
