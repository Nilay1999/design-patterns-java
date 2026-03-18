package com.lld.moviebooking.repository;

import com.lld.moviebooking.models.Show;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShowRepository {
    private final Map<String, Show> shows = new HashMap<>();

    public void add(Show show) {
        shows.put(show.getId(), show);
    }

    public Show get(String id) {
        return shows.get(id);
    }

    public List<Show> getAll() {
        return new ArrayList<>(shows.values());
    }

    public List<Show> getByMovie(String movieId) {
        List<Show> result = new ArrayList<>();
        for (Show show : shows.values()) {
            if (show.getMovie() != null && show.getMovie().getId().equals(movieId)) {
                result.add(show);
            }
        }
        return result;
    }
}
