package dao;
import database.DatabaseConnection;
import java.sql.*;
import java.math.BigDecimal;
public class PaymentDAO {
 public record Bill(String details,BigDecimal total) {}
 private Bill bill(Connection c,int table) throws SQLException {
  StringBuilder b=new StringBuilder();BigDecimal total=BigDecimal.ZERO;
  String q="SELECT f.food_name,i.quantity,i.unit_price FROM order_items i JOIN foods f ON f.food_id=i.food_id JOIN orders o ON o.order_id=i.order_id WHERE o.table_id=? AND o.status<>'PAID'";
  try(PreparedStatement s=c.prepareStatement(q)){s.setInt(1,table);try(ResultSet r=s.executeQuery()){
   while(r.next()){BigDecimal line=r.getBigDecimal(3).multiply(BigDecimal.valueOf(r.getInt(2)));total=total.add(line);b.append(r.getString(1)).append(" x").append(r.getInt(2)).append(" = ").append(line.toPlainString()).append("\n");}
  }}return new Bill(b.toString(),total);
 }
 public Bill bill(int table) throws SQLException {try(Connection c=DatabaseConnection.getConnection()){return bill(c,table);}}
 public Bill pay(int table) throws SQLException {
  try(Connection c=DatabaseConnection.getConnection()){
   c.setAutoCommit(false);
   try {
    try(PreparedStatement s=c.prepareStatement("SELECT table_id FROM restaurant_tables WHERE table_id=? FOR UPDATE")){s.setInt(1,table);try(ResultSet r=s.executeQuery()){if(!r.next())throw new IllegalArgumentException("Bàn không tồn tại");}}
    Bill bill=bill(c,table);if(bill.total().signum()==0)throw new IllegalArgumentException("Bàn không có đơn chưa thanh toán");
    try(PreparedStatement s=c.prepareStatement("SELECT COUNT(*) FROM order_items i JOIN orders o ON o.order_id=i.order_id WHERE o.table_id=? AND o.status<>'PAID' AND i.status<>'READY'")){s.setInt(1,table);try(ResultSet r=s.executeQuery()){r.next();if(r.getLong(1)>0)throw new IllegalArgumentException("Vẫn còn món chưa hoàn thành");}}
    // payments.order_id UNIQUE: one payment row per order; prices are stored at ordering time.
    String q="SELECT o.order_id,SUM(i.quantity*i.unit_price) amount FROM orders o JOIN order_items i ON i.order_id=o.order_id WHERE o.table_id=? AND o.status<>'PAID' GROUP BY o.order_id";
    try(PreparedStatement s=c.prepareStatement(q)){s.setInt(1,table);try(ResultSet r=s.executeQuery()){
     try(PreparedStatement ins=c.prepareStatement("INSERT INTO payments(order_id,amount) VALUES(?,?)")){
      while(r.next()){ins.setLong(1,r.getLong(1));ins.setBigDecimal(2,r.getBigDecimal(2));ins.executeUpdate();}
     }
    }}
    try(PreparedStatement s=c.prepareStatement("UPDATE orders SET status='PAID' WHERE table_id=? AND status<>'PAID'")){s.setInt(1,table);s.executeUpdate();}
    try(PreparedStatement s=c.prepareStatement("UPDATE restaurant_tables SET status='AVAILABLE' WHERE table_id=?")){s.setInt(1,table);s.executeUpdate();}
    c.commit();return bill;
   }catch(Exception e){c.rollback();if(e instanceof SQLException x)throw x;if(e instanceof RuntimeException x)throw x;throw new SQLException(e);}finally{c.setAutoCommit(true);}
  }
 }
}
