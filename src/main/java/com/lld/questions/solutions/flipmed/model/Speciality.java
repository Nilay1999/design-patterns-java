package com.lld.questions.solutions.flipmed.model;

public enum Speciality {
    CARDIOLOGIST, DERMATOLOGIST, ORTHOPEDIC, GENERAL_PHYSICIAN;

    public static Speciality fromString(String value) {
        try {
            return Speciality.valueOf(value.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown speciality: " + value);
        }
    }
}
