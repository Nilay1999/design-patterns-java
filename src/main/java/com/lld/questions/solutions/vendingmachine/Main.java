package com.lld.questions.solutions.vendingmachine;

public class Main {
    public static void main(String[] args) {
        // Start with an empty 2x2 grid of slots.
        Slot[][] slots = new Slot[2][2];
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                slots[r][c] = new Slot(null, 0);
            }
        }

        VendingMachine machine = new VendingMachine(slots);
        machine.addProduct(new Product(1, "Coke", 1.50), 0, 0, 5);
        machine.addProduct(new Product(2, "Water", 1.00), 0, 1, 3);

        // --- Happy path: buy a Coke with two coins, get change ---
        System.out.println("== Buy Coke (1.50) ==");
        machine.selectProduct(0, 0);
        machine.insertCash(1.00);
        System.out.println("after 1.00 -> " + machine.getCurrentTransaction().getStatus());
        machine.insertCash(1.00);
        System.out.println("after 2.00 -> " + machine.getCurrentTransaction().getStatus()
                + ", change=" + machine.getCurrentTransaction().getChange());
        Product bought = machine.dispense();
        System.out.println("dispensed " + bought.getName()
                + ", Coke stock now " + slots[0][0].getQuantity());

        // --- Cancel path: insert some money, then change your mind ---
        System.out.println("\n== Select Water, then cancel ==");
        machine.selectProduct(0, 1);
        machine.insertCash(0.50);
        double refund = machine.cancel();
        System.out.println("refunded " + refund + ", machine status " + machine.getStatus()
                + ", Water stock still " + slots[0][1].getQuantity());
    }
}
