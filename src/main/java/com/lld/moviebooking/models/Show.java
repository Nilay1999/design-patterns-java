package com.lld.moviebooking.models;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Show {
    public String id;
    public String startTime;
    public String endTime;
    public Movie movie;
    public List<Seat> seats;
}
