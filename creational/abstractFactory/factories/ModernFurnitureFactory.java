package creational.abstractFactory.factories;

import creational.abstractFactory.concreteProducts.ModernChair;
import creational.abstractFactory.concreteProducts.ModernTable;
import creational.abstractFactory.interfaces.Chair;
import creational.abstractFactory.interfaces.FurnitureFactory;
import creational.abstractFactory.interfaces.Table;

public class ModernFurnitureFactory implements FurnitureFactory {
  @Override
  public Chair createChair() {
    return new ModernChair();
  }

  @Override
  public Table createTable() {
    return new ModernTable();
  }
}
