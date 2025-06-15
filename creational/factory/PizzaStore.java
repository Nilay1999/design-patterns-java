package creational.factory;

import creational.factory.interfaces.Pizza;
import creational.factory.interfaces.PizzaFactory;
import creational.factory.product.MargheritaPizzaFactory;

public class PizzaStore {
  public static void main(String[] args) {
    PizzaFactory margherita = new MargheritaPizzaFactory();
    Pizza margheritaPizza = margherita.create();
    System.out.println(margheritaPizza);
  }
}
