package creational.abstractFactory.concreteProducts;

import creational.abstractFactory.interfaces.Table;

public class ModernTable implements Table {
  @Override
  public void use() {
    System.out.println("Using Modern Table!");
  }
}
