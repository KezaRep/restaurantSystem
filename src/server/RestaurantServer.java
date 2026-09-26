package server;

import model.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class RestaurantServer {

    // ═══════════════════════════════════════════
    //  STATE
    // ═══════════════════════════════════════════
    private final int port;
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private final Map<Long, Protocol.Order> orders = new LinkedHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1000);
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
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("[SERVER] Listening TCP port " + port);
            while (true) {
                Socket s = ss.accept();
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
    private synchronized String snapshot() {
        StringJoiner j = new StringJoiner("\n");
        for (var o : orders.values()) j.add(o.wire());
        return j.toString();
    }

    private void broadcast(String role, Message msg) {
        for (ClientHandler c : clients) {
            if (role == null || role.equals(c.role)) c.send(msg);
        }
    }

    private void snapshotAll() {
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
                case CONFIRM_PAYMENT -> handleConfirmPayment(c, table);
                case PING -> { }
                default -> c.error("Lệnh không được hỗ trợ");
            }
        } catch (Exception ex) {
            c.error(ex.getMessage() == null ? "Dữ liệu không hợp lệ" : ex.getMessage());
        }
    }

    // ─── REGISTER ───
    private void handleRegister(ClientHandler c, Message m) {
        if (!Set.of("STAFF", "KITCHEN").contains(m.getSenderRole())) {
            c.error("Vai trò không hợp lệ");
            return;
        }
        c.role = m.getSenderRole();
        c.send(new Message(Message.Type.SNAPSHOT, "SERVER", 0, snapshot()));
        System.out.println("[CONNECT] " + c.role + " " + c.socket.getRemoteSocketAddress());
    }

    // ─── REQUEST_SNAPSHOT ───
    private void handleRequestSnapshot(ClientHandler c) {
        if (c.authorized()) {
            c.send(new Message(Message.Type.SNAPSHOT, "SERVER", 0, snapshot()));
        }
    }

    // ─── CREATE_ORDER ───
    private void handleCreateOrder(ClientHandler c, Message m, int table) {
        if (!c.staff()) return;

        String[] x = m.getContent().split("\t", 3);
        if (table < 1 || table > 30 || x.length < 2) {
            throw new IllegalArgumentException("Thông tin đặt món không hợp lệ");
        }

        Menu.Dish d = Menu.get(x[0]);
        int qty = Integer.parseInt(x[1]);
        if (d == null || qty < 1 || qty > 30) {
            throw new IllegalArgumentException("Món hoặc số lượng không hợp lệ");
        }

        String note = x.length == 3 ? Protocol.decode(x[2]) : "";
        if (note.length() > 200) {
            throw new IllegalArgumentException("Ghi chú quá dài");
        }

        Protocol.Order o = new Protocol.Order(
                nextId.incrementAndGet(), table, d.id(), qty,
                "NEW", System.currentTimeMillis(), note);
        orders.put(o.id(), o);

        c.send(new Message(Message.Type.ORDER_ACK, "SERVER", table,
                "Đã gửi " + d.name() + " x" + qty + " đến bếp"));

        broadcast("KITCHEN", new Message(Message.Type.NEW_ORDER_NOTIFY, "SERVER", table, o.wire()));
        snapshotAll();

        System.out.println("[ORDER] " + o.id() + " table " + table);
    }

    // ─── UPDATE_COOK_STATUS ───
    private void handleUpdateCookStatus(ClientHandler c, Message m, int table) {
        if (!c.kitchen()) return;

        String[] x = m.getContent().split("\t", 2);
        long id = Long.parseLong(x[0]);
        String status = x.length > 1 ? x[1] : "";

        Protocol.Order old = orders.get(id);
        if (old == null || old.table() != table) {
            throw new IllegalArgumentException("Không tìm thấy đơn");
        }

        boolean valid = (old.status().equals("NEW") && status.equals("COOKING"))
                     || (old.status().equals("COOKING") && status.equals("READY"));
        if (!valid) {
            throw new IllegalArgumentException("Chuyển trạng thái không hợp lệ");
        }

        Protocol.Order o = new Protocol.Order(
                old.id(), old.table(), old.dish(), old.qty(),
                status, old.created(), old.note());
        orders.put(id, o);

        if (status.equals("READY")) {
            broadcast("STAFF", new Message(Message.Type.SERVE_NOTIFY, "SERVER", table, o.wire()));
        }
        snapshotAll();
    }

    // ─── REQUEST_CHECKOUT ───
    private void handleRequestCheckout(ClientHandler c, int table) {
        if (!c.staff()) return;

        long total = 0;
        StringBuilder b = new StringBuilder();

        for (var o : orders.values()) {
            if (o.table() == table) {
                Menu.Dish d = Menu.get(o.dish());
                if (d != null) {
                    long line = (long) d.price() * o.qty();
                    total += line;
                    b.append(d.name())
                     .append(" x").append(o.qty())
                     .append(" = ").append(line)
                     .append("\n");
                }
            }
        }

        if (total == 0) {
            throw new IllegalArgumentException("Bàn chưa có món để thanh toán");
        }

        c.send(new Message(Message.Type.BILL_INFO, "SERVER", table, b + "TOTAL\t" + total));
    }

    // ─── CONFIRM_PAYMENT ───
    private void handleConfirmPayment(ClientHandler c, int table) {
        if (!c.staff()) return;

        boolean exists = orders.values().stream().anyMatch(o -> o.table() == table);
        if (!exists) {
            throw new IllegalArgumentException("Bàn không có đơn");
        }

        boolean pending = orders.values().stream()
                .anyMatch(o -> o.table() == table && !o.status().equals("READY"));
        if (pending) {
            throw new IllegalArgumentException("Vẫn còn món chưa hoàn thành");
        }

        orders.values().removeIf(o -> o.table() == table);
        broadcast("STAFF", new Message(Message.Type.PAYMENT_DONE, "SERVER", table,
                "Bàn " + table + " đã thanh toán"));
        snapshotAll();
    }

    // ═══════════════════════════════════════════
    //  CLIENT HANDLER
    // ═══════════════════════════════════════════
    private class ClientHandler implements Runnable {

        final Socket socket;
        volatile String role = "";
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
                System.out.println("[DISCONNECT] " + role);
            }
        }
    }
}