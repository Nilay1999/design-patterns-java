package com.lld.questions.solutions.flipmed.model;

public class Appointment {

    public enum Status {
        CONFIRMED, WAITLISTED, CANCELLED
    }

    private final long id;
    private final Patient patient;
    private final Doctor doctor;
    private final int hour;
    private Status status;

    public Appointment(long id, Patient patient, Doctor doctor, int hour, Status status) {
        this.id = id;
        this.patient = patient;
        this.doctor = doctor;
        this.hour = hour;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public int getHour() {
        return hour;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String slotLabel() {
        return hour + ":00-" + (hour + 1) + ":00";
    }
}
