package com.lld.moviebooking.models;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Booking {
    public String id;
    public Movie movie;
    public User bookedBy;
    public Seat seat;
}
