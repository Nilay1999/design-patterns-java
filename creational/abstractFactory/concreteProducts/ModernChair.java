package creational.abstractFactory.concreteProducts;

import creational.abstractFactory.interfaces.Chair;

public class ModernChair implements Chair {
  @Override
  public void sitOn() {
    System.out.println("Sitting on a modern chair!");
  }
}
