package com.designpatterns.creational.factory;

public class PizzaStore {
    public static void main(String[] args) {
        String pizzaType = "veggie";
        Pizza pizza = PizzaMachine.makePizza(pizzaType);
        System.out.println(pizza.pack());
    }
}
