package com.lld.questions.solutions.flipmed.model;

import java.util.LinkedHashSet;
import java.util.Set;

public class Doctor {

    private final String name;
    private final Speciality speciality;
    private final double rating;
    private final Set<Integer> availableHours = new LinkedHashSet<>();

    public Doctor(String name, Speciality speciality, double rating) {
        this.name = name;
        this.speciality = speciality;
        this.rating = rating;
    }

    public void addAvailableHour(int hour) {
        availableHours.add(hour);
    }

    public boolean offers(int hour) {
        return availableHours.contains(hour);
    }

    public String getName() {
        return name;
    }

    public Speciality getSpeciality() {
        return speciality;
    }

    public double getRating() {
        return rating;
    }

    public Set<Integer> getAvailableHours() {
        return availableHours;
    }
}
