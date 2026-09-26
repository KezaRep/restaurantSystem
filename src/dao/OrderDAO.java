package dao;
import database.DatabaseConnection;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;
import model.Protocol;
public class OrderDAO {
 private final FoodDAO foods=new FoodDAO();
 // UI's legacy dish codes are mapped to numeric DB IDs in seed_restaurant.sql.
 public static int foodId(String code){
  if(code==null || !code.matches("[AMDB]\\d{2}"))throw new IllegalArgumentException("Mã món không hợp lệ");
  int n=Integer.parseInt(code.substring(1));
  return switch(code.charAt(0)){case 'A' -> n;case 'M' -> 6+n;case 'D' -> 14+n;case 'B' -> 18+n;default -> throw new IllegalArgumentException("Mã món không hợp lệ");};
 }
 public static String foodCode(int id){if(id>=1&&id<=6)return "A%02d".formatted(id);if(id>=7&&id<=14)return "M%02d".formatted(id-6);if(id>=15&&id<=18)return "D%02d".formatted(id-14);if(id>=19&&id<=24)return "B%02d".formatted(id-18);return String.valueOf(id);}
 public Protocol.Order create(int table,String code,int qty,String note,String requestId) throws SQLException {
  if(qty<1||qty>30||note.length()>200)throw new IllegalArgumentException("Số lượng/ghi chú không hợp lệ");
  try(Connection c=DatabaseConnection.getConnection()){
   c.setAutoCommit(false);
   try {
    try(PreparedStatement s=c.prepareStatement("SELECT table_id FROM restaurant_tables WHERE table_id=? FOR UPDATE")){
     s.setInt(1,table);try(ResultSet r=s.executeQuery()){if(!r.next())throw new IllegalArgumentException("Bàn chưa tồn tại trong database");}
    }
    FoodDAO.Food food=foods.findById(c,foodId(code));
    if(food==null||!food.available())throw new IllegalArgumentException("Món chưa có trong database hoặc hết hàng: "+code);
    long orderId;
    try(PreparedStatement s=c.prepareStatement("INSERT INTO orders(request_id,table_id,status) VALUES(?,?,'PENDING')",Statement.RETURN_GENERATED_KEYS)){
     s.setString(1,requestId);s.setInt(2,table);s.executeUpdate();try(ResultSet r=s.getGeneratedKeys()){if(!r.next())throw new SQLException("Không lấy được order_id");orderId=r.getLong(1);}
    }
    long itemId;
    try(PreparedStatement s=c.prepareStatement("INSERT INTO order_items(order_id,food_id,quantity,unit_price,status,note) VALUES(?,?,?,?,'PENDING',?)",Statement.RETURN_GENERATED_KEYS)){
     s.setLong(1,orderId);s.setInt(2,food.id());s.setInt(3,qty);s.setBigDecimal(4,food.price());s.setString(5,note);s.executeUpdate();try(ResultSet r=s.getGeneratedKeys()){if(!r.next())throw new SQLException("Không lấy được item_id");itemId=r.getLong(1);}
    }
    try(PreparedStatement s=c.prepareStatement("UPDATE restaurant_tables SET status='OCCUPIED' WHERE table_id=?")){s.setInt(1,table);s.executeUpdate();}
    c.commit();return new Protocol.Order(itemId,table,code,qty,"NEW",System.currentTimeMillis(),note);
   }catch(Exception e){c.rollback();if(e instanceof SQLException x)throw x;if(e instanceof RuntimeException x)throw x;throw new SQLException(e);}finally{c.setAutoCommit(true);}
  }
 }
 public List<Protocol.Order> active() throws SQLException {
  List<Protocol.Order> list=new ArrayList<>();
  String q="SELECT i.item_id,o.table_id,i.food_id,i.quantity,i.status,o.created_at,COALESCE(i.note,'') note FROM order_items i JOIN orders o ON o.order_id=i.order_id WHERE o.status<>'PAID' ORDER BY o.created_at,i.item_id";
  try(Connection c=DatabaseConnection.getConnection();PreparedStatement s=c.prepareStatement(q);ResultSet r=s.executeQuery()){
   while(r.next()){Timestamp t=r.getTimestamp(6);String st=r.getString(5);list.add(new Protocol.Order(r.getLong(1),r.getInt(2),foodCode(r.getInt(3)),r.getInt(4),"PENDING".equals(st)?"NEW":st,t==null?System.currentTimeMillis():t.getTime(),r.getString(7)));}
  }return list;
 }
 public Protocol.Order updateItem(long id,int table,String status) throws SQLException {
  if(!List.of("COOKING","READY").contains(status))throw new IllegalArgumentException("Trạng thái không hợp lệ");
  try(Connection c=DatabaseConnection.getConnection()){
   c.setAutoCommit(false);
   try {
    long orderId;String previous;int food,qty;String note;long created;
    String q="SELECT i.order_id,i.status,i.food_id,i.quantity,COALESCE(i.note,''),o.created_at FROM order_items i JOIN orders o ON o.order_id=i.order_id WHERE i.item_id=? AND o.table_id=? AND o.status<>'PAID' FOR UPDATE";
    try(PreparedStatement s=c.prepareStatement(q)){s.setLong(1,id);s.setInt(2,table);try(ResultSet r=s.executeQuery()){
     if(!r.next())throw new IllegalArgumentException("Không tìm thấy món");orderId=r.getLong(1);previous=r.getString(2);food=r.getInt(3);qty=r.getInt(4);note=r.getString(5);created=r.getTimestamp(6).getTime();
    }}
    if(!((previous.equals("PENDING")&&status.equals("COOKING"))||(previous.equals("COOKING")&&status.equals("READY"))))throw new IllegalArgumentException("Chuyển trạng thái không hợp lệ");
    try(PreparedStatement s=c.prepareStatement("UPDATE order_items SET status=? WHERE item_id=?")){s.setString(1,status);s.setLong(2,id);s.executeUpdate();}
    try(PreparedStatement s=c.prepareStatement("UPDATE orders SET status=CASE WHEN EXISTS(SELECT 1 FROM order_items WHERE order_id=? AND status='PENDING') THEN 'PENDING' WHEN EXISTS(SELECT 1 FROM order_items WHERE order_id=? AND status='COOKING') THEN 'COOKING' ELSE 'READY' END WHERE order_id=?")){s.setLong(1,orderId);s.setLong(2,orderId);s.setLong(3,orderId);s.executeUpdate();}
    c.commit();return new Protocol.Order(id,table,foodCode(food),qty,status,created,note);
   }catch(Exception e){c.rollback();if(e instanceof SQLException x)throw x;if(e instanceof RuntimeException x)throw x;throw new SQLException(e);}finally{c.setAutoCommit(true);}
  }
 }
}
