package com.designpatterns.creational.abstractfactory;

public class ModernTable implements Table {
  @Override
  public void use() {
    System.out.println("Using Modern Table!");
  }
}
