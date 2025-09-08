package com.designpatterns.creational.prototype;

public class Orc implements Character {
    private final int attack;
    private final int health;

    public Orc(int attack, int health) {
        this.attack = attack;
        this.health = health;
        // Expensive setup, e.g. loading 3D models, AI logic
        System.out.println("Orc created with attack: " + attack + " and health: " + health);
    }

    @Override
    public Character clone() {
        return new Orc(this.attack, this.health);
    }

    @Override
    public void display() {
        System.out.println("An Orc appears with " + attack + " and health " + health);
    }
}
