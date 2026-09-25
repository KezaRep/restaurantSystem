package server;

import model.Message;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class RestaurantServer {
    private static final int PORT = 8080;
    private static final Map<ClientHandler, String> clientRoles = new ConcurrentHashMap<>();
    private static final Map<Integer, List<OrderItem>> activeOrders = new ConcurrentHashMap<>();

    public static class OrderItem {
        private final String name;
        private final double price;

        public OrderItem(String name, double price) {
            this.name = name;
            this.price = price;
        }
        public String getName() { return name; }
        public double getPrice() { return price; }
    }

    public static void main(String[] args) {
        ExecutorService threadPool = Executors.newCachedThreadPool();
        System.out.println("[SERVER] Khởi chạy TCP Server tại cổng " + PORT + "...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                threadPool.execute(handler);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new PrintWriter(socket.getOutputStream(), true);

                String line;
                while ((line = reader.readLine()) != null) {
                    Message msg = Message.deserialize(line);
                    handleMessage(msg);
                }
            } catch (Exception e) {
                System.out.println("[DISCONNECT] Client ngắt kết nối.");
            } finally {
                clientRoles.remove(this);
                close();
            }
        }

        private void handleMessage(Message msg) {
            int tableNo = msg.getTableNo();

            switch (msg.getType()) {
                case REGISTER:
                    clientRoles.put(this, msg.getSenderRole());
                    System.out.println("[REGISTER] Kết nối vai trò: " + msg.getSenderRole());
                    break;

                case CREATE_ORDER:
                    activeOrders.computeIfAbsent(tableNo, k -> new ArrayList<>())
                            .add(new OrderItem(msg.getContent(), 50000));

                    System.out.println("[ORDER] Bàn " + tableNo + " order: " + msg.getContent());
                    msg.setType(Message.Type.NEW_ORDER_NOTIFY);
                    broadcastToRole("KITCHEN", msg);
                    break;

                case UPDATE_COOK_STATUS:
                    System.out.println("[KITCHEN] Bàn " + tableNo + " -> " + msg.getContent());
                    msg.setType(Message.Type.SERVE_NOTIFY);
                    broadcastToRole("STAFF", msg);
                    break;

                case REQUEST_CHECKOUT:
                    List<OrderItem> items = activeOrders.getOrDefault(tableNo, new ArrayList<>());
                    double total = items.stream().mapToDouble(OrderItem::getPrice).sum();

                    StringBuilder bill = new StringBuilder();
                    bill.append("--- HÓA ĐƠN BÀN ").append(tableNo).append(" ---\n");
                    for (OrderItem item : items) {
                        bill.append("- ").append(item.getName()).append(" : 50,000 VNĐ\n");
                    }
                    bill.append("TỔNG TIỀN: ").append(String.format("%,.0f", total)).append(" VNĐ");

                    Message billMsg = new Message(Message.Type.BILL_INFO, "SERVER", tableNo, bill.toString());
                    this.send(billMsg);
                    break;

                case CONFIRM_PAYMENT:
                    activeOrders.remove(tableNo);
                    System.out.println("[OK] Bàn " + tableNo + " đã thanh toán xong!");
                    break;
            }
        }

        public void send(Message msg) {
            writer.println(msg.serialize());
        }

        private void close() {
            try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        }
    }

    public static void broadcastToRole(String targetRole, Message msg) {
        clientRoles.forEach((handler, role) -> {
            if (targetRole.equalsIgnoreCase(role)) {
                handler.send(msg);
            }
        });
    }
}