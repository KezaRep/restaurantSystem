package dao;
import database.DatabaseConnection;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;
public class FoodDAO {
 public record Food(int id,String name,BigDecimal price,boolean available) {}
 public List<Food> findAll() throws SQLException {
  List<Food> result=new ArrayList<>();
  try(Connection c=DatabaseConnection.getConnection();PreparedStatement s=c.prepareStatement("SELECT food_id,food_name,price,available FROM foods ORDER BY food_id");ResultSet r=s.executeQuery()){
   while(r.next())result.add(new Food(r.getInt(1),r.getString(2),r.getBigDecimal(3),r.getBoolean(4)));
  } return result;
 }
 public Food findById(Connection c,int id) throws SQLException {
  try(PreparedStatement s=c.prepareStatement("SELECT food_id,food_name,price,available FROM foods WHERE food_id=?")){
   s.setInt(1,id);try(ResultSet r=s.executeQuery()){return r.next()?new Food(r.getInt(1),r.getString(2),r.getBigDecimal(3),r.getBoolean(4)):null;}
  }
 }
}
