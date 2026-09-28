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
   while(r.next()){BigDecimal line=r.getBigDecimal(3).multiply(BigDecimal.valueOf(r.getInt(2)));total=total.add(line);b.append(r.getString(1).replace("\t"," ").replace("\n"," ")).append("\t").append(r.getInt(2)).append("\t").append(r.getBigDecimal(3).toPlainString()).append("\t").append(line.toPlainString()).append("\n");}
  }}return new Bill(b.toString(),total);
 }
 public Bill bill(int table) throws SQLException {try(Connection c=DatabaseConnection.getConnection()){return bill(c,table);}}
 public Bill pay(int table) throws SQLException { return pay(table, "CASH"); }
 public Bill pay(int table,String method) throws SQLException {
  if (!java.util.Set.of("CASH", "CARD", "TRANSFER").contains(method)) throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ");
  try(Connection c=DatabaseConnection.getConnection()){
   c.setAutoCommit(false);
   try {
    try(PreparedStatement s=c.prepareStatement("SELECT table_id FROM restaurant_tables WHERE table_id=? FOR UPDATE")){s.setInt(1,table);try(ResultSet r=s.executeQuery()){if(!r.next())throw new IllegalArgumentException("Bàn không tồn tại");}}
    Bill bill=bill(c,table);if(bill.total().signum()==0)throw new IllegalArgumentException("Bàn không có đơn chưa thanh toán");
    try(PreparedStatement s=c.prepareStatement("SELECT COUNT(*) FROM order_items i JOIN orders o ON o.order_id=i.order_id WHERE o.table_id=? AND o.status<>'PAID' AND i.status<>'READY'")){s.setInt(1,table);try(ResultSet r=s.executeQuery()){r.next();if(r.getLong(1)>0)throw new IllegalArgumentException("Vẫn còn món chưa hoàn thành");}}
    // payments.order_id UNIQUE: one payment row per order; prices are stored at ordering time.
    String checkoutId=java.util.UUID.randomUUID().toString();
    String q="SELECT o.order_id,SUM(i.quantity*i.unit_price) amount FROM orders o JOIN order_items i ON i.order_id=o.order_id WHERE o.table_id=? AND o.status<>'PAID' GROUP BY o.order_id";
    try(PreparedStatement s=c.prepareStatement(q)){s.setInt(1,table);try(ResultSet r=s.executeQuery()){
     try(PreparedStatement ins=c.prepareStatement("INSERT INTO payments(order_id,amount,payment_method,checkout_id) VALUES(?,?,?,?)")){
      while(r.next()){ins.setLong(1,r.getLong(1));ins.setBigDecimal(2,r.getBigDecimal(2));ins.setString(3,method);ins.setString(4,checkoutId);ins.executeUpdate();}
     }
    }}
    try(PreparedStatement s=c.prepareStatement("UPDATE orders SET status='PAID' WHERE table_id=? AND status<>'PAID'")){s.setInt(1,table);s.executeUpdate();}
    try(PreparedStatement s=c.prepareStatement("UPDATE restaurant_tables SET status='AVAILABLE' WHERE table_id=?")){s.setInt(1,table);s.executeUpdate();}
    c.commit();return bill;
   }catch(Exception e){c.rollback();if(e instanceof SQLException x)throw x;if(e instanceof RuntimeException x)throw x;throw new SQLException(e);}finally{c.setAutoCommit(true);}
  }
 }
 public String history() throws SQLException {
  // One checkout = one row even when the table had multiple orders.
  // Wire: H\tcheckoutId(base64)\ttable\tamount\tpaidAt(epoch milliseconds)\tmethod(base64)
  //       I\tname(base64)\tquantity\tunitPrice\tlineTotal
  //       E
  StringBuilder out=new StringBuilder();
  String q="SELECT COALESCE(checkout_id,CONCAT('legacy-',payment_id)) checkout_id, " +
    "MIN(o.table_id) table_id,SUM(p.amount) amount,MAX(p.paid_at) paid_at, " +
    "MAX(p.payment_method) method FROM payments p JOIN orders o ON o.order_id=p.order_id " +
    "GROUP BY COALESCE(checkout_id,CONCAT('legacy-',payment_id)) ORDER BY paid_at DESC LIMIT 100";
  try(Connection c=DatabaseConnection.getConnection();PreparedStatement ps=c.prepareStatement(q);ResultSet rs=ps.executeQuery()){
   String iq="SELECT f.food_name,i.quantity,i.unit_price FROM payments p " +
      "JOIN order_items i ON i.order_id=p.order_id JOIN foods f ON f.food_id=i.food_id " +
      "WHERE COALESCE(p.checkout_id,CONCAT('legacy-',p.payment_id))=? ORDER BY i.item_id";
   try(PreparedStatement items=c.prepareStatement(iq)){
    while(rs.next()){
     String id=rs.getString(1);Timestamp ts=rs.getTimestamp(4);
     out.append("H\t").append(model.Protocol.encode(id)).append("\t").append(rs.getInt(2)).append("\t")
       .append(rs.getBigDecimal(3).toPlainString()).append("\t").append(ts==null?0:ts.getTime())
       .append("\t").append(model.Protocol.encode(rs.getString(5))).append("\n");
     items.setString(1,id);
     try(ResultSet ir=items.executeQuery()){
      while(ir.next()){
       BigDecimal price=ir.getBigDecimal(3);int qty=ir.getInt(2);
       out.append("I\t").append(model.Protocol.encode(ir.getString(1))).append("\t").append(qty)
         .append("\t").append(price.toPlainString()).append("\t")
         .append(price.multiply(BigDecimal.valueOf(qty)).toPlainString()).append("\n");
      }
     }
     out.append("E\n");
    }
   }
  }
  return out.toString();
 }
}
