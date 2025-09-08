# Design Patterns in Java

A comprehensive collection of design patterns implemented in Java, organized according to IntelliJ IDEA and Maven standards.

## Project Structure

This project follows the standard Maven directory structure:

```
src/
├── main/
│   └── java/
│       └── com/
│           └── designpatterns/
│               ├── creational/
│               │   ├── abstractfactory/
│               │   ├── builder/
│               │   ├── factory/
│               │   ├── prototype/
│               │   └── singleton/
│               └── structural/
│                   └── facade/
```

## Design Patterns Implemented

### Creational Patterns

#### 1. Abstract Factory Pattern

**Package:** `com.designpatterns.creational.abstractfactory`

Creates families of related objects without specifying their concrete classes.

**Classes:**

-   `FurnitureFactory` - Abstract factory interface
-   `ModernFurnitureFactory` - Concrete factory for modern furniture
-   `VictorianFurnitureFactory` - Concrete factory for Victorian furniture
-   `Chair` - Abstract product interface
-   `Table` - Abstract product interface
-   `ModernChair`, `ModernTable` - Concrete modern products
-   `VictorianChair`, `VictorianTable` - Concrete Victorian products
-   `Factory` - Main class demonstrating the pattern

#### 2. Builder Pattern

**Package:** `com.designpatterns.creational.builder`

Constructs complex objects step by step.

**Classes:**

-   `Pizza` - Product class with inner Builder
-   `Builder` - Main class demonstrating the pattern

#### 3. Factory Pattern

**Package:** `com.designpatterns.creational.factory`

Creates objects without specifying their exact classes.

**Classes:**

-   `PizzaFactory` - Abstract factory interface
-   `MargheritaPizzaFactory` - Concrete factory for Margherita pizza
-   `VeggiePizzaFactory` - Concrete factory for veggie pizza
-   `Pizza` - Abstract product interface
-   `MargheritaPizza`, `VeggiePizza` - Concrete products
-   `PizzaStore` - Main class demonstrating the pattern

#### 4. Prototype Pattern

**Package:** `com.designpatterns.creational.prototype`

Creates new objects by cloning an existing object.

**Classes:**

-   `Character` - Prototype interface
-   `Orc` - Concrete prototype implementation

#### 5. Singleton Pattern

**Package:** `com.designpatterns.creational.singleton`

Ensures a class has only one instance and provides global access to it.

**Classes:**

-   `CacheManager` - Thread-safe singleton with synchronized method
-   `DatabaseSingleton` - Singleton for database connection management

### Structural Patterns

#### 1. Facade Pattern

**Package:** `com.designpatterns.structural.facade`

Provides a simplified interface to a complex subsystem.

**Classes:**

-   `Restraunt` - Facade class
-   `Order` - Data class for orders
-   `OrderService` - Subsystem class for order management
-   `KitchenService` - Subsystem class for kitchen operations

## Getting Started

### Prerequisites

-   Java 11 or higher
-   Maven 3.6 or higher
-   IntelliJ IDEA (recommended)

### Setup

1. Clone the repository
2. Open the project in IntelliJ IDEA
3. Import as Maven project
4. Build the project using Maven: `mvn clean compile`

### Running Examples

Each pattern includes a main class that demonstrates its usage. You can run these directly from IntelliJ IDEA or using Maven:

```bash
# Run Abstract Factory example
mvn exec:java -Dexec.mainClass="com.designpatterns.creational.abstractfactory.Factory"

# Run Builder example
mvn exec:java -Dexec.mainClass="com.designpatterns.creational.builder.Builder"

# Run Factory example
mvn exec:java -Dexec.mainClass="com.designpatterns.creational.factory.PizzaStore"
```

## Project Configuration

### Maven Configuration

The project uses Maven for dependency management and build configuration. Key dependencies:

-   MySQL Connector (for DatabaseSingleton example)

### IntelliJ IDEA Configuration

-   Source folders are properly configured in `pom.xml`
-   Package structure follows Java conventions
-   All classes have proper package declarations

## Contributing

When adding new patterns:

1. Follow the existing package structure
2. Use appropriate package names (e.g., `com.designpatterns.behavioral.command` for behavioral patterns)
3. Include a main class demonstrating the pattern
4. Update this README with pattern descriptions

## Design Pattern Categories

This project organizes patterns into three main categories:

1. **Creational Patterns** - Deal with object creation mechanisms
2. **Structural Patterns** - Deal with object composition and relationships
3. **Behavioral Patterns** - Deal with communication between objects (to be added)

## Best Practices

-   Each pattern is implemented in its own package
-   All classes have proper package declarations
-   Examples include practical use cases
-   Code follows Java naming conventions
-   Thread safety is considered where applicable
