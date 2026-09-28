package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String HOST = "mysql-3f3645ac-restaurant-management990.g.aivencloud.com";
    private static final String PORT = "14981";

    private static final String DATABASE = "restaurant_db";

    private static final String USER = "avnadmin";

    private static final String PASSWORD = System.getenv("AIVEN_DB_PASSWORD"); ;

    public static Connection getConnection()
            throws SQLException {

        String url = "jdbc:mysql://" + HOST + ":" + PORT
                + "/" + DATABASE
                + "?sslMode=REQUIRED";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            System.out.println(
                "Đã tìm thấy MySQL JDBC Driver!"
            );

        } catch (ClassNotFoundException e) {

            throw new SQLException(
                "Không tìm thấy MySQL JDBC Driver. "
                + "Kiểm tra thư viện Connector/J.",
                e
            );
        }

        if (PASSWORD == null || PASSWORD.isBlank()) throw new SQLException("Set RESTAURANT_DB_PASSWORD environment variable");
        return DriverManager.getConnection(
                url,
                USER,
                PASSWORD
        );
    }
    

    public static void main(String[] args) {

        try (Connection conn = getConnection()) {

            System.out.println(
                "Kết nối Aiven MySQL thành công!"
            );

            System.out.println(
                "Database: " + conn.getCatalog()
            );

        } catch (SQLException e) {

            System.err.println(
                "Kết nối thất bại: " + e.getMessage()
            );
        }
    }
}