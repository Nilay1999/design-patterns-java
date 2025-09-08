package com.designpatterns.creational.factory;

public class VeggiePizza implements Pizza {
    public void console(String message) {
        System.out.println(message);
    }

    @Override
    public void prepare() {
        this.console("Preparing veggie pizza !");
    }

    @Override
    public void bake() {
        this.console("Baking veggie pizza !");
    }

    @Override
    public String pack() {
        return "Packing veggie pizza !";
    }
}
