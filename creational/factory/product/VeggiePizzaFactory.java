package creational.factory.product;

import creational.factory.creator.VeggiePizza;
import creational.factory.interfaces.Pizza;
import creational.factory.interfaces.PizzaFactory;

public class VeggiePizzaFactory implements PizzaFactory {
  @Override
  public Pizza create() {
    Pizza veggiePizza = new VeggiePizza();
    return veggiePizza;
  }
}
