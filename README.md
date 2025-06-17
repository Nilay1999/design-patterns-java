## 1. Singleton Pattern

**Intent:**  
Ensure a class has only one instance and provide a global point of access to it.

**How it’s implemented:**

-   **Private constructor:** Prevents external instantiation.
-   **Static instance:** Holds the single instance of the class.
-   **Thread-safe access:** Uses `synchronized` in the `getInstance()` method to ensure only one instance is created in a multithreaded environment.

**Examples in your code:**

-   `DatabaseSingleton`:  
    Manages a single database connection.
    -   The constructor is private.
    -   The static `getInstance()` method returns the single instance.
    -   The connection is established once and reused.
-   `CacheManager`:  
    Manages a single cache map for the application.
    -   Similar structure: private constructor, static instance, thread-safe `getInstance()`.

**Benefit:**  
Prevents multiple instances (e.g., multiple DB connections or caches), ensuring resource efficiency and consistent state.

## 2. Factory Pattern

**Intent:**  
Define an interface for creating an object, but let subclasses decide which class to instantiate.

**How it’s implemented:**

-   **Product interface:** Defines the contract for products (e.g., `Pizza`).
-   **Concrete products:** Implement the product interface (e.g., `MargheritaPizza`, `VeggiePizza`).
-   **Factory interface:** Declares a method for creating products (e.g., `PizzaFactory`).
-   **Concrete factories:** Implement the factory interface to instantiate specific products (e.g., `MargheritaPizzaFactory`, `VeggiePizzaFactory`).

**Examples in your code:**

-   `Pizza`:  
    The product interface with methods like `prepare()`, `bake()`, `pack()`.
-   `MargheritaPizza`, `VeggiePizza`:  
    Concrete pizza types.
-   `PizzaFactory`:  
    The factory interface.
-   `MargheritaPizzaFactory`, `VeggiePizzaFactory`:  
    Factories that create specific pizza types.
-   `PizzaStore`:  
    Demonstrates usage by creating pizzas via factories.

**Benefit:**  
Encapsulates object creation, making it easy to add new types without changing client code.

## 3. Abstract Factory Pattern

**Intent:**  
Provide an interface for creating families of related or dependent objects without specifying their concrete classes.

**How it’s implemented:**

-   **Abstract factory interface:** Declares methods for creating abstract products (e.g., `FurnitureFactory` with `createChair()` and `createTable()`).
-   **Abstract product interfaces:** Define contracts for products (e.g., `Chair`, `Table`).
-   **Concrete factories:** Implement the abstract factory to create related products (e.g., `ModernFurnitureFactory`, `VictorianFurnitureFactory`).
-   **Concrete products:** Implement the product interfaces (e.g., `ModernChair`, `VictorianChair`, `ModernTable`, `VictorianTable`).

**Examples in your code:**

-   `FurnitureFactory`:  
    Abstract factory interface.
-   `Chair`, `Table`:  
    Abstract product interfaces.
-   `ModernFurnitureFactory`, `VictorianFurnitureFactory`:  
    Concrete factories.
-   `ModernChair`, `VictorianChair`, etc.:  
    Concrete products.
-   `Factory`:  
    Demonstrates creating families of related products.

**Benefit:**  
Ensures products created by a factory are compatible, and makes it easy to switch between product families.

## 4. Facade Pattern

**Intent:**  
Provide a unified interface to a set of interfaces in a subsystem, making the subsystem easier to use.

**How it’s implemented:**

-   **Facade class:** Offers simplified methods that internally coordinate calls to subsystem classes (e.g., `Restraunt`).
-   **Subsystem classes:** Handle the actual work (e.g., `OrderService`, `KitchenService`).
-   **Domain objects:** Represent data (e.g., `Order`).

**Examples in your code:**

-   `Restraunt`:  
    The facade class. Methods like `placeOrder()` and `completeOrder()` coordinate the order and kitchen services.
-   `OrderService`:  
    Handles order creation.
-   `KitchenService`:  
    Handles order preparation and serving.
-   `Order`:  
    Represents an order.

**Benefit:**  
Simplifies complex subsystems, reduces coupling, and provides a clear API for clients.

## Summary Table

| Pattern          | Intent                                                         | Example Classes/Files               |
| ---------------- | -------------------------------------------------------------- | ----------------------------------- |
| Singleton        | Single instance, global access                                 | `DatabaseSingleton`, `CacheManager` |
| Factory          | Interface for object creation, subclass decides implementation | `PizzaStore`, `PizzaFactory`        |
| Abstract Factory | Interface for families of related objects                      | `FurnitureFactory`, `Factory`       |
| Facade           | Unified interface to subsystem                                 | `Restraunt`, `OrderService`         |

---
