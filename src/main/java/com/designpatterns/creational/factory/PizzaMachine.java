package com.designpatterns.creational.factory;

public class PizzaMachine {
    public static Pizza makePizza(String type) {
        switch (type) {
            case "veggie":
                return new VeggiePizza();
            case "margherita":
                return new MargheritaPizza();
            default:
                return null;
        }
    }
}
