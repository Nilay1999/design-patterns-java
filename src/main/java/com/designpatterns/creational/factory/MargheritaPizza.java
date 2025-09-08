package com.designpatterns.creational.factory;

public class MargheritaPizza implements Pizza {
    public void console(String message) {
        System.out.println(message);
    }

    @Override
    public void prepare() {
        this.console("Preparing Margherita pizza !");
    }

    @Override
    public void bake() {
        this.console("Baking Margherita pizza !");
    }

    @Override
    public String pack() {
        return "Packing Margherita pizza !";
    }
}