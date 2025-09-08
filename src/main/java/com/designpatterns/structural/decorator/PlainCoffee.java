package com.designpatterns.structural.decorator;

public class PlainCoffee implements Coffee {

    public String getDescription() {
        return "Plain Coffee";
    }

    public double getCost() {
        return 2.0;
    }
}