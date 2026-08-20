package com.lld.questions.solutions.atmmachine;

import java.util.List;
import java.util.Map;

public class ATM {
    private String atmId;
    private List<Bank> banks;
    private AtmState atmState;
    private Card insertedCard;
    private CashDispenser cashDispenser;

    public ATM(String atmId, List<Bank> banks, AtmState atmState, CashDispenser cashDispenser) {
        this.atmId = atmId;
        this.banks = banks;
        this.atmState = AtmState.IDLE;
        this.cashDispenser = cashDispenser;
    }

    public void insertCard(Card card) {
        if (this.atmState != AtmState.IDLE) {
            throw new RuntimeException("ATM machine is currently processing transaction");
        }
        this.insertedCard = card;
        this.atmState = AtmState.CARD_INSERTED;
    }

    public boolean enterPinAndAuthenticate(int pinNumber) {
        if (atmState == AtmState.CARD_INSERTED && this.insertedCard.verifyPinNumber(pinNumber)) {
            this.atmState = AtmState.AUTHENTICATED;
            return true;
        }
        return false;
    }

    public void performTransaction(TransactionType transactionType, int amount) {
        if (atmState != AtmState.AUTHENTICATED) {
            throw new RuntimeException("Authenticate before performing a transaction");
        }

        Account acc = this.insertedCard.getAccount();
        this.atmState = AtmState.PROCESSING_TRANSACTION;

        switch (transactionType) {
            case WITHDRAW: {
                if (acc.hasSufficientBalance(amount) && cashDispenser.canDispense(amount)) {
                    acc.withdraw(amount);
                    Map<Denomination, Integer> dispensed = cashDispenser.dispenseCash(amount);
                    System.out.println("Dispensed " + amount + " as " + dispensed
                            + ". Balance: " + acc.getBalance());
                } else {
                    System.out.println("Withdrawal failed: insufficient funds or cash unavailable.");
                }
                break;
            }

            case DEPOSIT: {
                acc.deposite(amount);
                System.out.println("Deposited " + amount + ". Balance: " + acc.getBalance());
                break;
            }

            case BALANCE_INQUIRY: {
                System.out.println("Balance: " + acc.getBalance());
                break;
            }
        }

        // Back to the menu so the customer can run another operation.
        this.atmState = AtmState.AUTHENTICATED;
    }

    public void ejectCard() {
        this.atmState = AtmState.EJECTING_CARD;
        this.insertedCard = null;
        this.atmState = AtmState.IDLE;
        System.out.println("Card ejected.");
    }

    public String getAtmId() {
        return atmId;
    }

    public void setAtmId(String atmId) {
        this.atmId = atmId;
    }

    public List<Bank> getBanks() {
        return banks;
    }

    public void setBanks(List<Bank> banks) {
        this.banks = banks;
    }

    public AtmState getAtmState() {
        return atmState;
    }

    public void setAtmState(AtmState atmState) {
        this.atmState = atmState;
    }

    public Card getInsertedCard() {
        return insertedCard;
    }
}
