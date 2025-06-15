package creational.factory.creator;

import creational.factory.interfaces.Pizza;

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
  public void pack() {
    this.console("Packing veggie pizza !");
  }
}
