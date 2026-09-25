package model;

public class Message {
    public enum Type {
        REGISTER,           // Đăng ký vai trò (STAFF hoặc KITCHEN)
        CREATE_ORDER,       // Phục vụ đặt món
        NEW_ORDER_NOTIFY,   // Server chuyển đơn sang Bếp
        UPDATE_COOK_STATUS, // Bếp báo làm xong
        SERVE_NOTIFY,        // Server báo Phục vụ lấy món
        REQUEST_CHECKOUT,   // Phục vụ xin bill
        BILL_INFO,          // Server gửi bill
        CONFIRM_PAYMENT,    // Phục vụ xác nhận thu tiền
        SNAPSHOT, 
        ERROR,
        ORDER_ACK, 
        PAYMENT_DONE, 
        REQUEST_SNAPSHOT,
        PING, ADD_ITEM, ORDER_UPDATE
    }

    private Type type;
    private String senderRole; 
    private int tableNo;
    private String content;

    public Message(Type type, String senderRole, int tableNo, String content) {
        this.type = type;
        this.senderRole = senderRole;
        this.tableNo = tableNo;
        this.content = content;
    }

    public String serialize() {
        return type.name() + "|" + senderRole + "|" + tableNo + "|" + content.replace("\n", "#N#");
    }

    public static Message deserialize(String line) {
        String[] parts = line.split("\\|", 4);
        Type type = Type.valueOf(parts[0]);
        String role = parts[1];
        int tableNo = Integer.parseInt(parts[2]);
        String content = parts[3].replace("#N#", "\n");
        return new Message(type, role, tableNo, content);
    }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getSenderRole() { return senderRole; }
    public int getTableNo() { return tableNo; }
    public String getContent() { return content; }
}