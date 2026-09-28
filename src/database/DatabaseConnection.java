package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {

    private static final String HOST =
            "mysql-3f3645ac-restaurant-management990.g.aivencloud.com";

    private static final String PORT = "14981";

    private static final String DATABASE = "restaurant_db";

    private static final String USER = "avnadmin";

    private static final String PASSWORD = System.getenv("RESTAURANT_DB_PASSWORD");;

    public static Connection getConnection()
            throws SQLException {

        if (PASSWORD == null || PASSWORD.isBlank()) {
            throw new SQLException(
                "Chưa thiết lập biến môi trường RESTAURANT_DB_PASSWORD"
            );
        }

        String url = "jdbc:mysql://" + HOST + ":" + PORT
                + "/" + DATABASE
                + "?sslMode=REQUIRED"
                + "&connectionTimeZone=UTC"
                + "&forceConnectionTimeZoneToSession=true";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

        } catch (ClassNotFoundException e) {

            throw new SQLException(
                "Không tìm thấy MySQL JDBC Driver. "
                + "Hãy kiểm tra thư viện MySQL Connector/J.",
                e
            );
        }

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

            try (
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                    "SELECT "
                    + "@@session.time_zone AS session_tz, "
                    + "NOW() AS db_now, "
                    + "UTC_TIMESTAMP() AS utc_now"
                )
            ) {

                if (rs.next()) {

                    System.out.println(
                        "Session timezone: "
                        + rs.getString("session_tz")
                    );

                    System.out.println(
                        "Database time: "
                        + rs.getString("db_now")
                    );

                    System.out.println(
                        "UTC time: "
                        + rs.getString("utc_now")
                    );
                }
            }

        } catch (SQLException e) {

            System.err.println(
                "Kết nối thất bại: " + e.getMessage()
            );

            e.printStackTrace();
        }
    }
}