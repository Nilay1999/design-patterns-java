package com.lld.moviebooking.models;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Booking {
    public String id;
    public Movie movie;
    public User bookedBy;
    public List<Seat> seat;
    public BookingStatus status;

    public Booking(String id, Movie movie, User bookedBy, List<Seat> seat) {
        this.id = id;
        this.movie = movie;
        this.bookedBy = bookedBy;
        this.seat = seat;
        this.status = BookingStatus.PENDING;
    }
}
