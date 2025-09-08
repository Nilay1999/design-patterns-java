package com.designpatterns.creational.singleton;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseSingleton {

    // Static variable reference of single_instance of type DatabaseSingleton
    private static DatabaseSingleton instance = null;

    // JDBC connection
    private Connection connection;

    // Private constructor to restrict instantiation from other classes
    private DatabaseSingleton() {
        try {
            // Database credentials
            String url = "jdbc:mysql://localhost:3306/mydb";
            String password = "password";
            String username = "root";
            this.connection = DriverManager.getConnection(url, username, password);
            System.out.println("Database connected successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Public method to provide access to the instance
    // Using `synchronized` for Thread Safety
    public static synchronized DatabaseSingleton getInstance() {
        if (instance == null) {
            instance = new DatabaseSingleton();
        }
        return instance;
    }

    // Getter for the actual Connection object
    public Connection getConnection() {
        return connection;
    }
}

/*
 * Example Usage
 */

// public class Main {
//     public static void main(String[] args) {
//         DatabaseSingleton db1 = DatabaseSingleton.getInstance();
//         Connection conn1 = db1.getConnection();

//         DatabaseSingleton db2 = DatabaseSingleton.getInstance();
//         Connection conn2 = db2.getConnection();

//         System.out.println("Are both connections the same? " + (conn1 == conn2));
//     }
// }