package com.designpatterns.creational.prototype;

public interface Character extends Cloneable {
  Character clone();

  void display();
}
