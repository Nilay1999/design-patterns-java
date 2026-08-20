package com.lld.questions.solutions.atmmachine;

public enum AtmState {
    IDLE,
    CARD_INSERTED,
    AUTHENTICATED,
    PROCESSING_TRANSACTION,
    EJECTING_CARD
}
