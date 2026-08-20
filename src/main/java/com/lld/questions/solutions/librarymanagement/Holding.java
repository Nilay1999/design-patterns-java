package com.lld.questions.solutions.librarymanagement;

import java.time.Instant;

public class Holding {
    private int id;
    private Member member;
    private BookCopy book;
    private Instant rentedOn;
    private Instant givenBack;
    private Instant deadline;

    public Holding(int id, Member member, BookCopy book, Instant rentedOn, Instant deadline) {
        this.id = id;
        this.member = member;
        this.book = book;
        this.rentedOn = rentedOn;
        this.deadline = deadline;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    public BookCopy getBook() {
        return book;
    }

    public void setBook(BookCopy book) {
        this.book = book;
    }

    public Instant getRentedOn() {
        return rentedOn;
    }

    public void setRentedOn(Instant rentedOn) {
        this.rentedOn = rentedOn;
    }

    public Instant getGivenBack() {
        return givenBack;
    }

    public void setGivenBack(Instant givenBack) {
        this.givenBack = givenBack;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public void setDeadline(Instant deadline) {
        this.deadline = deadline;
    }
}
