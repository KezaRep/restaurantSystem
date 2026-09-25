package client;

import model.Message;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class StaffClient {
    public static void main(String[] args) {
        try {
            Socket socket = new Socket("localhost", 8080);// chỗ này là ip mạng
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

            // Đăng ký vai trò STAFF
            Message reg = new Message(Message.Type.REGISTER, "STAFF", 0, "NONE");
            writer.println(reg.serialize());
            System.out.println("[STAFF APP] Đã kết nối hệ thống Phục vụ!");
            System.out.println("Cú pháp: <Số bàn> <Tên món> | pay <Số bàn> | ok <Số bàn>");

            // Luồng ngầm lắng nghe thông báo
            new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Message msg = Message.deserialize(line);

                        if (msg.getType() == Message.Type.SERVE_NOTIFY) {
                            System.out.println("\n🔔 [BÁO LẤY MÓN] Bàn " + msg.getTableNo() + ": " + msg.getContent());
                        } else if (msg.getType() == Message.Type.BILL_INFO) {
                            System.out.println("\n==================================");
                            System.out.println(msg.getContent());
                            System.out.println("==================================");
                        }
                        System.out.print("Nhập lệnh: ");
                    }
                } catch (IOException e) {
                    System.out.println("Mất kết nối.");
                }
            }).start();

            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("Nhập lệnh: ");
                String input = scanner.nextLine().trim();
                if ("exit".equalsIgnoreCase(input) || input.isEmpty()) continue;

                String[] parts = input.split(" ", 2);

                if ("pay".equalsIgnoreCase(parts[0]) && parts.length > 1) {
                    int tableNo = Integer.parseInt(parts[1]);
                    writer.println(new Message(Message.Type.REQUEST_CHECKOUT, "STAFF", tableNo, "NONE").serialize());
                } else if ("ok".equalsIgnoreCase(parts[0]) && parts.length > 1) {
                    int tableNo = Integer.parseInt(parts[1]);
                    writer.println(new Message(Message.Type.CONFIRM_PAYMENT, "STAFF", tableNo, "NONE").serialize());
                    System.out.println("-> Đã giải phóng bàn " + tableNo);
                } else if (parts.length == 2) {
                    int tableNo = Integer.parseInt(parts[0]);
                    writer.println(new Message(Message.Type.CREATE_ORDER, "STAFF", tableNo, parts[1]).serialize());
                    System.out.println("-> Đã gửi order thành công!");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}