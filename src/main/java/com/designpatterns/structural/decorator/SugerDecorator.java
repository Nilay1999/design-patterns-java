package com.designpatterns.structural.decorator;

public class SugerDecorator extends CoffeeDecorator{
    public SugerDecorator(Coffee decoratedCoffee) {
        super(decoratedCoffee);
    }

    @Override
    public String getDescription() {
        return decoratedCoffee.getDescription() + " with Suger";
    }

    @Override
    public double getCost() {
        return decoratedCoffee.getCost() + 1.0;
    }
}
