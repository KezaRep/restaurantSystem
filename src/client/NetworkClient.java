package client;

import model.Message;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class NetworkClient {

    private Socket socket;
    private PrintWriter out;
    private volatile boolean connected;

    private final String role;
    private final Consumer<Message> onMessage;
    private final Consumer<String> onState;

    public NetworkClient(String role,
                         Consumer<Message> onMessage,
                         Consumer<String> onState) {
        this.role = role;
        this.onMessage = onMessage;
        this.onState = onState;
    }

    public void connect(String host, int port) {
        new Thread(() -> {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 4000);

                out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                        true);
                connected = true;
                onState.accept("Đã kết nối • " + host + ":" + port);

                send(new Message(Message.Type.REGISTER, role, 0, ""));

                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                    String line;
                    while ((line = r.readLine()) != null) {
                        try {
                            onMessage.accept(Message.deserialize(line));
                        } catch (RuntimeException ex) {
                            onState.accept("Lỗi dữ liệu nhận được");
                        }
                    }
                }

            } catch (Exception ex) {
                onState.accept("Không thể kết nối: " + ex.getMessage());

            } finally {
                connected = false;
                onState.accept("Mất kết nối");
                try {
                    if (socket != null) socket.close();
                } catch (IOException ignored) {}
            }
        }, "TCP-" + role).start();
    }

    public synchronized void send(Message m) {
        if (connected && out != null) {
            out.println(m.serialize());
            if (out.checkError()) onState.accept("Gửi thất bại");
        } else {
            onState.accept("Chưa kết nối server");
        }
    }

    public void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }
}