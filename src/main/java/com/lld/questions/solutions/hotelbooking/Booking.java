package com.lld.questions.solutions.hotelbooking;

import java.time.Instant;

public class Booking {
    private String bookingId;
    private BookingStatus status;
    private Instant startDate;
    private Instant endDate;
    private Instant holdExpiresAt;
    private User user;
    private Room room;
    private Payment payment;

    public Booking(String bookingId, BookingStatus status, Instant startDate, Instant endDate, Instant holdExpiresAt,
            User user, Room room, Payment payment) {
        this.bookingId = bookingId;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.holdExpiresAt = holdExpiresAt;
        this.user = user;
        this.room = room;
        this.payment = payment;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }

    public Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public void setHoldExpiresAt(Instant holdExpiresAt) {
        this.holdExpiresAt = holdExpiresAt;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }
}
