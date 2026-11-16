package in.railwaydemo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Db {
    private Db() {}

    public static Connection open() throws SQLException {
        String url = System.getenv().getOrDefault("RAILWAY_DB_URL", "jdbc:postgresql://localhost:5432/railway_ticket_db");
        String user = System.getenv().getOrDefault("RAILWAY_DB_USER", "railway_app");
        String password = System.getenv("RAILWAY_DB_PASSWORD");
        if (password == null || password.isBlank()) throw new SQLException("RAILWAY_DB_PASSWORD is not set");
        return DriverManager.getConnection(url, user, password);
    }
}
