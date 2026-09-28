package server;

import model.*;
import dao.*;
import database.DatabaseConnection;
import java.sql.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;


public class RestaurantServer {

    // ═══════════════════════════════════════════
    //  STATE
    // ═══════════════════════════════════════════
    private final int port;
    private volatile boolean running;
    private volatile ServerSocket listener;
    private final java.util.List<java.util.function.Consumer<String>> observers = new java.util.concurrent.CopyOnWriteArrayList<>();
    public void onActivity(java.util.function.Consumer<String> callback) { observers.add(callback); }
    private void log(String line) { System.out.println(line); observers.forEach(o -> o.accept(line)); }
    public void stop() { running = false; try { if(listener != null) listener.close(); } catch(IOException ignored) {} for(ClientHandler c: clients) try { c.socket.close(); } catch(IOException ignored) {} }
    public java.util.List<String> connectedUsers() { return clients.stream().filter(c -> !c.role.isEmpty()).map(c -> c.username+" | "+c.role+" | ONLINE").sorted().toList(); }
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private final OrderDAO orderDAO = new OrderDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    // ═══════════════════════════════════════════
    //  KHỞI TẠO
    // ═══════════════════════════════════════════
    public RestaurantServer(int port) {
        this.port = port;
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5001;
        new RestaurantServer(port).start();
    }

    // ═══════════════════════════════════════════
    //  VÒNG LẶP CHÍNH
    // ═══════════════════════════════════════════
    public void start() throws IOException {
        try (Connection test = DatabaseConnection.getConnection()) {
            System.out.println("[DATABASE] Connected to " + test.getCatalog());
            orderDAO.active();
        } catch (SQLException ex) { throw new IOException("Aiven database unavailable: " + ex.getMessage(), ex); }
        try (ServerSocket ss = new ServerSocket(port)) {
            listener = ss; running = true;
            log("[SERVER] Listening TCP port " + port);
            while (running) {
                Socket s; try { s = ss.accept(); } catch(SocketException ex) { if(!running) break; throw ex; }
                s.setKeepAlive(true);

                ClientHandler h = new ClientHandler(s);
                clients.add(h);
                pool.execute(h);
            }
        }
    }

    // ═══════════════════════════════════════════
    //  SNAPSHOT + BROADCAST
    // ═══════════════════════════════════════════
    private String snapshot() throws SQLException {
        StringJoiner j = new StringJoiner("\n");
        for (var o : orderDAO.active()) j.add(o.wire());
        return j.toString();
    }

    private void broadcast(String role, Message msg) {
        for (ClientHandler c : clients) {
            if (role == null || role.equals(c.role)) c.send(msg);
        }
    }

    private void snapshotAll() throws SQLException {
        Message m = new Message(Message.Type.SNAPSHOT, "SERVER", 0, snapshot());
        broadcast(null, m);
    }

    // ═══════════════════════════════════════════
    //  XỬ LÝ MESSAGE
    // ═══════════════════════════════════════════
    private synchronized void handle(ClientHandler c, Message m) {
        int table = m.getTableNo();
        try {
            switch (m.getType()) {

                case REGISTER -> handleRegister(c, m);
                case REQUEST_SNAPSHOT -> handleRequestSnapshot(c);
                case CREATE_ORDER -> handleCreateOrder(c, m, table);
                case UPDATE_COOK_STATUS -> handleUpdateCookStatus(c, m, table);
                case REQUEST_CHECKOUT -> handleRequestCheckout(c, table);
                case CONFIRM_PAYMENT -> handleConfirmPayment(c, m, table);
                case REQUEST_PAYMENT_HISTORY -> { if(c.cashier()) c.send(new Message(Message.Type.PAYMENT_HISTORY,"SERVER",0,paymentDAO.history())); }
                case ADD_ITEM -> handleCreateOrder(c,m,table);
                case PING -> { }
                default -> c.error("Lệnh không được hỗ trợ");
            }
        } catch (Exception ex) {
            c.error(ex.getMessage() == null ? "Dữ liệu không hợp lệ" : ex.getMessage());
        }
    }

    // ─── REGISTER ───
    private void handleRegister(ClientHandler c, Message m) throws SQLException {
        if (!Set.of("STAFF", "KITCHEN", "CASHIER").contains(m.getSenderRole())) {
            c.error("Vai trò không hợp lệ");
            return;
        }
        if(m.getContent().isBlank() || m.getContent().length() > 60) throw new IllegalArgumentException("Tên nhân viên không hợp lệ");
        c.role = m.getSenderRole(); c.username = m.getContent().trim();
        c.send(new Message(Message.Type.AUTH_OK,"SERVER",0,c.username));
        c.send(new Message(Message.Type.SNAPSHOT, "SERVER", 0, snapshot()));
        log("[CONNECT] " + c.username + " (" + c.role + ") " + c.socket.getRemoteSocketAddress());
    }

    // ─── REQUEST_SNAPSHOT ───
    private void handleRequestSnapshot(ClientHandler c) throws SQLException {
        if (c.authorized()) {
            c.send(new Message(Message.Type.SNAPSHOT, "SERVER", 0, snapshot()));
        }
    }

    // One CREATE_ORDER message creates one order and one item, preserving legacy UI protocol.
    private void handleCreateOrder(ClientHandler c, Message m, int table) throws SQLException {
        if (!Set.of("STAFF", "CASHIER").contains(c.role)) { c.error("Không có quyền"); return; }
        String[] x = m.getContent().split("\t", 3);
        if (x.length < 2) throw new IllegalArgumentException("Thiếu mã món hoặc số lượng");
        Menu.Dish dish = Menu.get(x[0]);
        if (dish == null) throw new IllegalArgumentException("Món không tồn tại trong giao diện");
        int qty = Integer.parseInt(x[1]);
        String note = x.length == 3 ? Protocol.decode(x[2]) : "";
        Protocol.Order order = orderDAO.create(table, dish.id(), qty, note, UUID.randomUUID().toString(),c.username);
        c.send(new Message(Message.Type.ORDER_ACK, "SERVER", table, "Đã gửi " + dish.name() + " x" + qty + " đến bếp"));
        broadcast("KITCHEN", new Message(Message.Type.NEW_ORDER_NOTIFY, "SERVER", table, order.wire()));
        snapshotAll();
    }
    private void handleUpdateCookStatus(ClientHandler c, Message m, int table) throws SQLException {
        if (!c.kitchen()) return;
        String[] x = m.getContent().split("\t", 2);
        if (x.length != 2) throw new IllegalArgumentException("Thiếu trạng thái");
        Protocol.Order order = orderDAO.updateItem(Long.parseLong(x[0]), table, x[1]);
        if (order.status().equals("READY")) broadcast("STAFF", new Message(Message.Type.SERVE_NOTIFY, "SERVER", table, order.wire()));
        snapshotAll();
    }
    private void handleRequestCheckout(ClientHandler c, int table) throws SQLException {
        if (!Set.of("STAFF", "CASHIER").contains(c.role)) { c.error("Không có quyền"); return; }
        PaymentDAO.Bill bill = paymentDAO.bill(table);
        if (bill.total().signum() == 0) throw new IllegalArgumentException("Bàn chưa có món để thanh toán");
        c.send(new Message(Message.Type.BILL_INFO, "SERVER", table, bill.details() + "TOTAL\t" + bill.total().toPlainString()));
    }
    private void handleConfirmPayment(ClientHandler c, Message m, int table) throws SQLException {
        if (!c.cashier()) return;
        paymentDAO.pay(table, m.getContent());
        log("[PAYMENT] " + c.username + " paid table " + table + " via " + m.getContent());
        broadcast(null, new Message(Message.Type.PAYMENT_DONE, "SERVER", table, "Bàn " + table + " đã thanh toán"));
        broadcast("CASHIER",new Message(Message.Type.PAYMENT_HISTORY,"SERVER",0,paymentDAO.history()));
        snapshotAll();
    }

    // ═══════════════════════════════════════════
    //  CLIENT HANDLER
    // ═══════════════════════════════════════════
    private class ClientHandler implements Runnable {

        final Socket socket;
        volatile String role = "";
        volatile String username = "";
        PrintWriter writer;

        ClientHandler(Socket s) {
            socket = s;
        }

        // ─── Kiểm tra quyền ───
        boolean authorized() {
            if (role.isEmpty()) {
                error("Vui lòng đăng ký vai trò");
                return false;
            }
            return true;
        }

        boolean staff() {
            if (!"STAFF".equals(role)) {
                error("Chỉ phục vụ được thao tác này");
                return false;
            }
            return true;
        }

        boolean cashier() { if (!"CASHIER".equals(role)) { error("Chỉ thu ngân được thao tác này"); return false; } return true; }

        boolean kitchen() {
            if (!"KITCHEN".equals(role)) {
                error("Chỉ bếp được thao tác này");
                return false;
            }
            return true;
        }

        // ─── Gửi / báo lỗi ───
        synchronized void send(Message m) {
            if (writer != null) {
                writer.println(m.serialize());
                if (writer.checkError()) {
                    System.err.println("Send failed: " + socket);
                }
            }
        }

        void error(String s) {
            send(new Message(Message.Type.ERROR, "SERVER", 0, s));
        }

        // ─── Vòng lặp đọc ───
        @Override
        public void run() {
            try (socket;
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                writer = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                        true);

                String line;
                while ((line = in.readLine()) != null) {
                    try {
                        handle(this, Message.deserialize(line));
                    } catch (Exception ex) {
                        error("Gói tin không hợp lệ");
                    }
                }

            } catch (IOException ignored) {
            } finally {
                clients.remove(this);
                log("[DISCONNECT] " + username + " (" + role + ")");
            }
        }
    }
}