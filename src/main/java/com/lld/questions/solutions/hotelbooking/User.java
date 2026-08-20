package com.lld.questions.solutions.hotelbooking;

import java.util.List;

public class User {
    private String userId;
    private String email;
    private String username;
    private Integer age;
    private Gender gender;
    private List<Booking> bookings;

    public User(String userId, String email, String username, Integer age, Gender gender, List<Booking> bookings) {
        this.userId = userId;
        this.email = email;
        this.username = username;
        this.age = age;
        this.gender = gender;
        this.bookings = bookings;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public List<Booking> getBookings() {
        return bookings;
    }

    public void setBookings(List<Booking> bookings) {
        this.bookings = bookings;
    }
}
