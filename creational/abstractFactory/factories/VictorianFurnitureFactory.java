package creational.abstractFactory.factories;

import creational.abstractFactory.concreteProducts.VictorianChair;
import creational.abstractFactory.concreteProducts.VictorianTable;
import creational.abstractFactory.interfaces.Chair;
import creational.abstractFactory.interfaces.FurnitureFactory;
import creational.abstractFactory.interfaces.Table;

public class VictorianFurnitureFactory implements FurnitureFactory {
  @Override
  public Chair createChair() {
    return new VictorianChair();
  }

  @Override
  public Table createTable() {
    return new VictorianTable();
  }
}
