package client;

import model.Message;

import java.io.*;
import java.net.Socket;

public class KitchenClient {
    public static void main(String[] args) {
        try {
            Socket socket = new Socket("localhost", 8080);// test thì nói để tuấn chạy server thử
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

            // Đăng ký KITCHEN
            writer.println(new Message(Message.Type.REGISTER, "KITCHEN", 0, "NONE").serialize());
            System.out.println("[KITCHEN KDS] Màn hình Bếp đã bật...\n");

            String line;
            while ((line = reader.readLine()) != null) {
                Message msg = Message.deserialize(line);

                if (msg.getType() == Message.Type.NEW_ORDER_NOTIFY) {
                    System.out.println("🔥 [ĐƠN MỚI] BÀN " + msg.getTableNo() + " -> Món: " + msg.getContent());
                    System.out.println("⏳ Đang chế biến (4s)...");
                    Thread.sleep(4000);

                    Message doneMsg = new Message(
                            Message.Type.UPDATE_COOK_STATUS,
                            "KITCHEN",
                            msg.getTableNo(),
                            "Món [" + msg.getContent() + "] ĐÃ XONG!"
                    );
                    writer.println(doneMsg.serialize());
                    System.out.println("✅ Đã báo Phục vụ ra lấy món Bàn " + msg.getTableNo() + "\n");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}