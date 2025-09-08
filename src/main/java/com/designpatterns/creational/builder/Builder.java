package com.designpatterns.creational.builder;

public class Builder {
  public static void main(String[] args) {
    Pizza myPizza = new Pizza.Builder("Large")
        .addCheese()
        .addPepperoni()
        .addMushrooms()
        .build();

    System.out.println(myPizza);
  }
}
