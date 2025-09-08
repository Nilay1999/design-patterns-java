package com.designpatterns.creational.builder;

public class Pizza {
  // Required parameters
  private final String size;

  // Optional parameters
  private final boolean cheese;
  private final boolean pepperoni;
  private final boolean mushrooms;
  private final boolean onions;

  private Pizza(Builder builder) {
    this.size = builder.size;
    this.cheese = builder.cheese;
    this.pepperoni = builder.pepperoni;
    this.mushrooms = builder.mushrooms;
    this.onions = builder.onions;
  }

  public static class Builder {
    // Required
    private final String size;

    // Optional - default to false
    private boolean cheese = false;
    private boolean pepperoni = false;
    private boolean mushrooms = false;
    private boolean onions = false;

    public Builder(String size) {
      this.size = size;
    }

    public Builder addCheese() {
      this.cheese = true;
      return this;
    }

    public Builder addPepperoni() {
      this.pepperoni = true;
      return this;
    }

    public Builder addMushrooms() {
      this.mushrooms = true;
      return this;
    }

    public Builder addOnions() {
      this.onions = true;
      return this;
    }

    public Pizza build() {
      return new Pizza(this);
    }
  }

  @Override
  public String toString() {
    return "Pizza [size=" + size +
        ", cheese=" + cheese +
        ", pepperoni=" + pepperoni +
        ", mushrooms=" + mushrooms +
        ", onions=" + onions + "]";
  }
}
