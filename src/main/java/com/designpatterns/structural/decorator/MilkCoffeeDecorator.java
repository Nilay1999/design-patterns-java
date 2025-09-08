package com.designpatterns.structural.decorator;

public class MilkCoffeeDecorator extends CoffeeDecorator{
    public MilkCoffeeDecorator(Coffee decoratedCoffee) {
        super(decoratedCoffee);
    }

    @Override
    public String getDescription() {
        return decoratedCoffee.getDescription() + ", Milk";
    }

    @Override
    public double getCost() {
        return decoratedCoffee.getCost() + 0.5;
    }

    public static void main(String[] args) {
        Coffee coffee = new PlainCoffee();
        System.out.println(coffee.getDescription());
        System.out.println(coffee.getCost());

        Coffee milkCoffee = new MilkCoffeeDecorator(coffee);
        System.out.println(milkCoffee.getDescription());
        System.out.println(milkCoffee.getCost());

        Coffee sugerCoffee = new SugerDecorator(milkCoffee);
        System.out.println(sugerCoffee.getDescription());
        System.out.println(sugerCoffee.getCost());
    }
}
