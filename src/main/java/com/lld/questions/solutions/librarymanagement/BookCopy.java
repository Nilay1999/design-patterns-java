package com.lld.questions.solutions.librarymanagement;

public class BookCopy {
    private int id;
    private Book book;
    private boolean isAvailable;

    public BookCopy(int id, Book book) {
        this.id = id;
        this.book = book;
        this.isAvailable = true;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Book getBook() {
        return book;
    }

    public void setBook(Book book) {
        this.book = book;
    }

    public synchronized boolean isAvailable() {
        return isAvailable;
    }

    public synchronized void rentBook() {
        this.isAvailable = false;
    }

    public synchronized void returnBook() {
        this.isAvailable = true;
    }
}
