package dao;

import database.DatabaseConnection;

import java.sql.*;

public class TableDAO {

    public void seedTables() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement s = c.prepareStatement(
                     "INSERT IGNORE INTO restaurant_tables(table_id, table_name, status) " +
                     "VALUES(?,?,'AVAILABLE')")) {

            for (int i = 1; i <= 30; i++) {
                s.setInt(1, i);
                s.setString(2, "Bàn " + i);
                s.addBatch();
            }
            s.executeBatch();
        }
    }
}