package com.pdm.connect;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class dbconnect {
    private static final String DEFAULT_PROPERTIES_PATH = "db.properties";

    private dbconnect() {
    }

    public static Connection getConnection() throws SQLException {
        Properties setting = new Properties();

        try (InputStream input = new FileInputStream(DEFAULT_PROPERTIES_PATH)) {
            setting.load(input);
        } catch (IOException ex) {
            throw new SQLException("Error reading db.properties", ex);
        }

        String url = setting.getProperty("db.url");
        String user = setting.getProperty("db.user");
        String password = setting.getProperty("db.password");

        if (url == null || url.isBlank()) {
            throw new SQLException("db.url is missing in db.properties");
        }

        return DriverManager.getConnection(url.trim(), user != null ? user.trim() : "",
                password != null ? password : "");
    }

    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
            return false;
        }
    }

    public static void closeConnection() {
        // Connections are short-lived and closed by try-with-resources in DAOs.
    }
}
