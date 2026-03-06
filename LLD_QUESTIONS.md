# Low Level Design (LLD) Interview Questions

A curated list of LLD problems for practice. Each question focuses on class design, relationships, and applying OOP principles and design patterns.

> Already implemented in this repo: Parking System, Snake & Ladder, URL Shortener, Key-Value Store, Garbage Collector

---

## Beginner

### 1. Library Management System
Design a system to manage books, members, and borrowing.

**Requirements:**
- Add/remove books and members
- Member can borrow and return books
- Track due dates and overdue fines
- Search books by title, author, or ISBN
- A book can have multiple copies

**Think about:** Book vs BookItem, Reservation system, Fine calculation strategy

---

### 2. ATM Machine
Design an ATM that handles cash withdrawals and balance inquiries.

**Requirements:**
- User authenticates with card + PIN
- Check balance, withdraw cash, deposit cash
- ATM has limited cash in denominations (100, 500, 2000)
- Dispense minimum number of notes
- Handle insufficient balance and insufficient cash in ATM

**Think about:** State pattern (Idle, CardInserted, PinEntered, TransactionInProgress), denomination dispensing algorithm

---

### 3. Vending Machine
Design a vending machine that sells products.

**Requirements:**
- Select product and insert coins/cash
- Dispense product and return change
- Admin can restock items and collect cash
- Handle edge cases: out of stock, insufficient money, no change available

**Think about:** State pattern (Idle, ProductSelected, MoneyInserted), coin denomination management

---

### 4. Logging Framework
Design a flexible logging system.

**Requirements:**
- Support log levels: DEBUG, INFO, WARN, ERROR, FATAL
- Multiple appenders: Console, File, Database
- Log format customization
- Filter logs by level or pattern
- Thread-safe logging

**Think about:** Chain of Responsibility for log levels, Strategy for formatters, Observer for appenders

---

### 5. Task Scheduler
Design a task scheduler that runs jobs at specified intervals.

**Requirements:**
- Schedule one-time and recurring tasks (cron-like)
- Cancel scheduled tasks
- Handle task priorities
- Limit concurrent task execution
- Retry failed tasks with backoff

**Think about:** Priority Queue, Thread pool, State for task lifecycle

---

## Intermediate

### 6. Hotel Booking System
Design a hotel room booking system.

**Requirements:**
- Search available rooms by date range and type (Single, Double, Suite)
- Book, modify, and cancel reservations
- Apply pricing rules (weekday vs weekend, seasonal)
- Check-in and check-out flow
- Generate invoices with itemized billing

**Think about:** Strategy for pricing, Observer for notifications, Builder for Invoice

---

### 7. Chess Game
Design a two-player chess game.

**Requirements:**
- Model the board, pieces, and their moves
- Validate legal moves for each piece type
- Detect check, checkmate, and stalemate
- Support special moves: castling, en passant, pawn promotion
- Track game history (move log)

**Think about:** Piece hierarchy (abstract Piece with concrete types), Board as 8x8 grid, Command pattern for move history

---

### 8. Food Delivery System (like Swiggy/Zomato)
Design a food ordering and delivery system.

**Requirements:**
- Customers browse restaurants and menus
- Place orders with multiple items
- Assign delivery agents based on proximity
- Real-time order status updates
- Rating system for restaurants and delivery agents

**Think about:** Observer for status updates, Strategy for delivery assignment, State for order lifecycle

---

### 9. Elevator System
Design an elevator controller for a multi-floor building.

**Requirements:**
- Multiple elevators in the building
- User presses up/down button on a floor
- Elevator dispatching algorithm (nearest, SCAN/LOOK)
- Handle simultaneous requests efficiently
- Direction state per elevator

**Think about:** State pattern (Moving Up, Moving Down, Idle, Door Open), Strategy for dispatching algorithm

---

### 10. Movie Ticket Booking (like BookMyShow)
Design a seat booking system for movie theaters.

**Requirements:**
- Browse movies, theaters, and showtimes
- View seat map and select seats
- Reserve seats temporarily during payment (seat locking)
- Confirm or release seats based on payment outcome
- Prevent double booking

**Think about:** Seat locking with TTL, Concurrency with synchronized blocks or optimistic locking, Strategy for seat pricing

---

### 11. Ride Sharing App (like Uber)
Design a ride-hailing service.

**Requirements:**
- Riders request rides with pickup and drop location
- Match riders to nearest available drivers
- Real-time location tracking
- Fare calculation based on distance and time
- Ratings for both rider and driver

**Think about:** Observer for location updates, Strategy for fare calculation and driver matching, State for ride lifecycle

---

### 12. Online Shopping Cart
Design a shopping cart and checkout system.

**Requirements:**
- Add/remove items, update quantities
- Apply coupon codes and discounts
- Calculate taxes based on product category and region
- Multiple payment methods (card, wallet, COD)
- Order placement and inventory update

**Think about:** Strategy for discount and tax, Chain of Responsibility for discount application order, Observer for inventory

---

## Advanced

### 13. Rate Limiter
Design a rate limiter to control API request rates.

**Requirements:**
- Limit requests per user/IP per time window
- Support algorithms: Token Bucket, Leaky Bucket, Fixed Window, Sliding Window
- Configurable limits per endpoint
- Thread-safe and high-performance
- Return appropriate HTTP 429 responses

**Think about:** Strategy pattern for algorithm selection, Sliding window log vs counter, Distributed rate limiting considerations

---

### 14. Pub-Sub Messaging System
Design a publish-subscribe event system.

**Requirements:**
- Publishers push messages to topics
- Subscribers subscribe to topics and receive messages
- Support multiple message delivery modes (at-most-once, at-least-once)
- Message persistence and replay
- Dead-letter queue for failed deliveries

**Think about:** Observer pattern at scale, message queuing, subscriber offset tracking

---

### 15. Cache System (like LRU Cache)
Design a configurable in-memory cache.

**Requirements:**
- Support eviction policies: LRU, LFU, FIFO, TTL-based
- Thread-safe get/put/delete operations
- Configurable max capacity
- Cache hit/miss statistics
- Optional write-through and write-back modes

**Think about:** Strategy for eviction policy, DoublyLinkedList + HashMap for O(1) LRU, decorator for statistics

---

### 16. Notification Service
Design a multi-channel notification system.

**Requirements:**
- Send notifications via Email, SMS, Push, Slack
- User preferences for notification channels and quiet hours
- Template-based notification content
- Retry failed deliveries with exponential backoff
- Batch notifications to avoid spam

**Think about:** Strategy for channels, Observer for event-driven triggers, Chain of Responsibility for retry logic

---

### 17. Online Code Judge (like LeetCode)
Design a system that accepts and evaluates code submissions.

**Requirements:**
- Users submit code solutions for problems
- Execute code in isolated sandboxes with time and memory limits
- Support multiple languages (Java, Python, C++)
- Return verdict: Accepted, Wrong Answer, TLE, MLE, RE
- Maintain leaderboard per problem

**Think about:** Strategy for language runners, State for submission lifecycle, Observer for result notifications

---

### 18. LinkedIn / Social Network
Design a professional social network.

**Requirements:**
- User profiles with work experience and skills
- Connection requests (1st, 2nd, 3rd degree)
- Post feed with likes and comments
- Job postings and applications
- Recommendation feed (people you may know)

**Think about:** Graph for connections, Observer for feed updates, Strategy for feed ranking

---

### 19. Google Calendar
Design a calendar and event scheduling system.

**Requirements:**
- Create, edit, delete events with time, location, and attendees
- Recurring events (daily, weekly, monthly)
- Invite attendees and track RSVP status
- Conflict detection for overlapping events
- Reminders and notifications

**Think about:** Composite for recurring events, Observer for reminders, Strategy for recurrence rules

---

### 20. Traffic Signal Controller
Design a traffic light management system for an intersection.

**Requirements:**
- Manage signals for multiple lanes at an intersection
- Cycle through Green, Yellow, Red states with configurable durations
- Emergency vehicle override
- Adaptive timing based on traffic density
- Coordinate multiple intersections

**Think about:** State pattern for signal states, Strategy for timing algorithm, Observer for emergency events

---

## Tips for Answering LLD Questions

1. **Clarify requirements** — Ask about scale, edge cases, and must-have vs nice-to-have features
2. **Identify entities** — List the nouns (classes) and verbs (methods/behaviors)
3. **Define relationships** — IS-A (inheritance) vs HAS-A (composition)
4. **Apply SOLID principles** — Single Responsibility, Open/Closed, Liskov, Interface Segregation, Dependency Inversion
5. **Pick relevant design patterns** — Don't force patterns; let requirements drive the choice
6. **Handle concurrency** — Identify shared state and protect it
7. **Think about extensibility** — New payment methods, new notification channels, etc.

## Design Pattern Quick Reference

| Pattern | When to Use in LLD |
|---|---|
| **Strategy** | Swappable algorithms (pricing, sorting, matching) |
| **State** | Object behavior changes based on internal state |
| **Observer** | Event-driven updates (notifications, feeds) |
| **Factory/Abstract Factory** | Create objects without coupling to concrete classes |
| **Builder** | Construct complex objects step by step |
| **Decorator** | Add behavior without modifying existing classes |
| **Singleton** | Single shared resource (config, connection pool) |
| **Command** | Encapsulate actions (undo/redo, job queues) |
| **Chain of Responsibility** | Sequential processing (middleware, discount rules) |
| **Composite** | Tree structures (file systems, org charts) |
