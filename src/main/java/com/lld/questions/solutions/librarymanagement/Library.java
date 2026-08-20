package com.lld.questions.solutions.librarymanagement;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Library {
    private static final long LOAN_PERIOD_DAYS = 14;

    private List<Book> books = new ArrayList<>();
    private List<BookCopy> bookCopies = new ArrayList<>();
    private List<Member> members = new ArrayList<>();
    private List<Holding> holding = new ArrayList<>();
    private List<Fine> fines = new ArrayList<>();
    private FineStrategy fineStrategy;
    private int MAX_HOLDING = 5;
    private int nextHoldingId = 1;
    private int nextFineId = 1;

    public Library() {
        this(new FlatRateFineStrategy());
    }

    public Library(FineStrategy fineStrategy) {
        this.fineStrategy = fineStrategy;
    }

    public List<Book> getBooks() {
        return books;
    }

    public void setBooks(List<Book> books) {
        this.books = books;
    }

    public List<BookCopy> getBookCopies() {
        return bookCopies;
    }

    public void setBookCopies(List<BookCopy> bookCopies) {
        this.bookCopies = bookCopies;
    }

    public List<Member> getMembers() {
        return members;
    }

    public void setMembers(List<Member> members) {
        this.members = members;
    }

    public List<Holding> getHolding() {
        return holding;
    }

    public void setHolding(List<Holding> holding) {
        this.holding = holding;
    }

    public List<Fine> getFines() {
        return fines;
    }

    public int getMaxHolding() {
        return MAX_HOLDING;
    }

    public void setMaxHolding(int MAX_HOLDING) {
        this.MAX_HOLDING = MAX_HOLDING;
    }

    public int countActiveHoldings(Member member) {
        return (int) holding.stream()
                .filter(h -> h.getMember().getId() == member.getId() && h.getGivenBack() == null)
                .count();
    }

    public List<Book> searchBook(Genre genre, String bookTitle, String author) {
        return books.stream()
                .filter(book -> {
                    if (genre != null && book.getGenre() != genre) {
                        return false;
                    }

                    if (bookTitle != null && !bookTitle.isEmpty() &&
                            !book.getTitle().toLowerCase().contains(bookTitle.toLowerCase())) {
                        return false;
                    }

                    if (author != null && !author.isEmpty()) {
                        String firstName = book.getAuthor().getFirstName();
                        String lastName = book.getAuthor().getLastName();
                        boolean firstNameMatches = firstName.toLowerCase().contains(author.toLowerCase());
                        boolean lastNameMatches = lastName.toLowerCase().contains(author.toLowerCase());
                        if (!firstNameMatches && !lastNameMatches) {
                            return false;
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());
    }

    public boolean rentBook(Member member, Book book) {
        if (countActiveHoldings(member) >= MAX_HOLDING) {
            return false;
        }

        BookCopy rentedBook = null;
        for (BookCopy copy : bookCopies) {
            if (copy.getBook().equals(book) && copy.isAvailable()) {
                copy.rentBook();
                rentedBook = copy;
                break;
            }
        }
        if (rentedBook == null) {
            return false;
        }

        Instant now = Instant.now();
        Instant deadline = now.plus(Duration.ofDays(LOAN_PERIOD_DAYS));
        Holding loan = new Holding(nextHoldingId++, member, rentedBook, now, deadline);
        holding.add(loan);
        return true;
    }

    public boolean returnBook(Member member, BookCopy copy) {
        Holding activeHolding = null;
        for (Holding h : holding) {
            if (h.getMember().getId() == member.getId()
                    && h.getBook().getId() == copy.getId()
                    && h.getGivenBack() == null) {
                activeHolding = h;
                break;
            }
        }
        if (activeHolding == null) {
            return false;
        }

        activeHolding.setGivenBack(Instant.now());
        copy.returnBook();

        Fine fine = new Fine(nextFineId++, activeHolding, fineStrategy);
        if (fine.calculateFine() > 0) {
            fines.add(fine);
        }
        return true;
    }
}
