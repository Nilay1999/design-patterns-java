package creational.abstractFactory.concreteProducts;

import creational.abstractFactory.interfaces.Table;

public class VictorianTable implements Table {
  @Override
  public void use() {
    System.out.println("Using Victorian Table!");
  }
}
