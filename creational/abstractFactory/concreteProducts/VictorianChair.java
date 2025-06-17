package creational.abstractFactory.concreteProducts;

import creational.abstractFactory.interfaces.Chair;

public class VictorianChair implements Chair {
  @Override
  public void sitOn() {
    System.out.println("Sitting on a modern chair!");
  }
}