package creational.factory.product;

import creational.factory.creator.MargheritaPizza;
import creational.factory.interfaces.Pizza;
import creational.factory.interfaces.PizzaFactory;

public class MargheritaPizzaFactory implements PizzaFactory {
  @Override
  public Pizza create() {
    Pizza margheritaPizza = new MargheritaPizza();
    return margheritaPizza;
  }
}
