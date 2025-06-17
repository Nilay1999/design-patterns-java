package creational.abstractFactory;

import creational.abstractFactory.factories.ModernFurnitureFactory;
import creational.abstractFactory.factories.VictorianFurnitureFactory;
import creational.abstractFactory.interfaces.Chair;
import creational.abstractFactory.interfaces.FurnitureFactory;
import creational.abstractFactory.interfaces.Table;

public class Factory {
    public static void main(String[] args) {

        FurnitureFactory modernFactory = new ModernFurnitureFactory();
        Chair modernChair = modernFactory.createChair();
        Table modernTable = modernFactory.createTable();

        // Use the modern furniture
        modernChair.sitOn();
        modernTable.use();

        // Create Victorian furniture using the VictorianFurnitureFactory
        FurnitureFactory victorianFactory = new VictorianFurnitureFactory();
        Chair victorianChair = victorianFactory.createChair();
        Table victorianTable = victorianFactory.createTable();

        // Use the Victorian furniture
        victorianChair.sitOn();
        victorianTable.use();
    }
}