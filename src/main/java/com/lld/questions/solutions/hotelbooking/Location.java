package com.lld.questions.solutions.hotelbooking;

public class Location {
    private String locationId;
    private String city;
    private String state;
    private String pinCode;
    private String addressLine1;
    private String addressLine2;
    private double longitude;
    private double latitude;

    public Location(String locationId, String city, String state, String pinCode, String addressLine1,
            String addressLine2, double longitude, double latitude) {
        this.locationId = locationId;
        this.city = city;
        this.state = state;
        this.pinCode = pinCode;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.longitude = longitude;
        this.latitude = latitude;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(String pinCode) {
        this.pinCode = pinCode;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }
}
