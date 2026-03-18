package com.lld.moviebooking.models;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Seat {
    public String id;
    public Show show;
    public User occupiedBy;
    public SeatStatus status;
    public Instant lockedAt;
}
