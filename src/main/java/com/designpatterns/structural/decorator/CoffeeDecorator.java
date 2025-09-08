package com.designpatterns.structural.decorator;

public abstract class CoffeeDecorator implements Coffee{
    protected Coffee decoratedCoffee;

    public CoffeeDecorator(Coffee decoratedCoffee) {
        this.decoratedCoffee = decoratedCoffee;
    }

    @Override
    public String getDescription() {
        return decoratedCoffee.getDescription();
    }

    public double getPrice() {
        return decoratedCoffee.getCost();
    }
}
