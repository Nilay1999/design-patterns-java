package com.lld.moviebooking.repository;

import com.lld.moviebooking.models.Movie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MovieRepository {
    private final Map<String, Movie> movies = new HashMap<>();

    public void add(Movie movie) {
        movies.put(movie.getId(), movie);
    }

    public Movie get(String id) {
        return movies.get(id);
    }

    public List<Movie> getAll() {
        return new ArrayList<>(movies.values());
    }
}
