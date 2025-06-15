package creational.factory.creator;

import creational.factory.interfaces.Pizza;

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
  public void pack() {
    this.console("Packing Margherita pizza !");
  }
}