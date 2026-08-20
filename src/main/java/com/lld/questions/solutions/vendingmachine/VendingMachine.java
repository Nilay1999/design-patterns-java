package com.lld.questions.solutions.vendingmachine;

public class VendingMachine {
    private Slot[][] slots;
    private VendingMachineStatus status;
    // The purchase currently in progress. Null when the machine is idle.
    private Transaction current;

    public VendingMachine(Slot[][] slots) {
        this.slots = slots;
        this.status = VendingMachineStatus.IDLE;
        this.current = null;
    }

    public void addProduct(Product product, int row, int col, int quantity) {
        Slot slot = slots[row][col];
        if (slot.getProduct() != null) {
            slot.setQuantity(slot.getQuantity() + quantity);
        } else {
            slots[row][col] = new Slot(product, quantity);
        }
    }

    // --- Purchase flow ---

    // Pick a slot to buy from. Opens a transaction for the slot's price.
    public Transaction selectProduct(int row, int col) {
        if (status != VendingMachineStatus.IDLE) {
            throw new IllegalStateException("Machine busy: finish or cancel the current purchase first");
        }
        Slot slot = slots[row][col];
        if (slot.getProduct() == null || slot.getQuantity() <= 0) {
            throw new IllegalStateException("Slot is empty / out of stock");
        }
        current = new Transaction(slot, slot.getProduct().getPrice());
        status = VendingMachineStatus.AWAITING_PAYMENT;
        return current;
    }

    // Feed money into the current transaction. May be called multiple times
    // until the bill is covered (Transaction flips itself to PAID).
    public void insertCash(double amount) {
        if (current == null) {
            throw new IllegalStateException("Select a product first");
        }
        current.applyPayment(amount);
    }

    // Hand over the product once paid: drop stock, return the product, and
    // reset the machine. Change is reported separately via getChange().
    public Product dispense() {
        if (current == null) {
            throw new IllegalStateException("No active purchase");
        }
        if (current.getStatus() != TransactionStatus.PAID) {
            throw new IllegalStateException("Not enough money inserted yet");
        }
        status = VendingMachineStatus.DISPENSING;
        Slot slot = current.getSlot();
        slot.setQuantity(slot.getQuantity() - 1);
        Product product = slot.getProduct();
        current.setStatus(TransactionStatus.DISPENSED);
        reset();
        return product;
    }

    // Abort the current purchase and refund whatever was inserted.
    public double cancel() {
        if (current == null) {
            return 0;
        }
        double refund = current.getAmountPaid();
        current.setStatus(TransactionStatus.CANCELLED);
        reset();
        return refund;
    }

    private void reset() {
        current = null;
        status = VendingMachineStatus.IDLE;
    }

    // --- Accessors ---

    public Slot[][] getSlots() {
        return slots;
    }

    public VendingMachineStatus getStatus() {
        return status;
    }

    public Transaction getCurrentTransaction() {
        return current;
    }
}
