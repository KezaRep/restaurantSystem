package ui;

import model.Menu;
import model.Message;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class CustomerFrame extends BaseFrame {

    private JPanel menuHost;

    public CustomerFrame() {
        super("SAVORY — Khách hàng", "CUSTOMER");
        showHome();
    }

    @Override protected String[] sideItems() {
        return new String[]{"Trang chủ", "Menu", "Đơn của tôi", "Bàn"};
    }

    @Override protected String subtitle() {
        return "Đặt món ngon — phục vụ tận bàn";
    }

    @Override protected String role() { return "CUSTOMER"; }

    @Override protected void render() {
        // vẽ lại theo state nếu cần
    }

    @Override protected void navigateTo(String item) {
        switch (item) {
            case "Trang chủ" -> showHome();
            case "Menu"      -> showMenu();
            case "Đơn của tôi" -> showOrders();
            case "Bàn"       -> showTableInfo();
        }
    }

    // ─── Các trang ───
    private void showHome() {
        setBreadcrumb("Trang chủ");
        content.removeAll();

        JPanel home = Theme.panel(new BorderLayout(0, 20));
        home.add(Theme.label("Chào mừng đến SAVORY 🍽", 28, true, Theme.NAVY),
                BorderLayout.NORTH);

        JPanel shortcuts = Theme.panel(new GridLayout(1, 3, 16, 0));
        shortcuts.add(shortcutCard("Xem Menu", "Chọn món yêu thích", () -> showMenu()));
        shortcuts.add(shortcutCard("Đơn của tôi", "Theo dõi đơn hàng",   this::showOrders));
        shortcuts.add(shortcutCard("Bàn ăn",     "Xem trạng thái bàn",   this::showTableInfo));
        home.add(shortcuts, BorderLayout.CENTER);

        content.add(home, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    private JPanel shortcutCard(String title, String sub, Runnable action) {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 6));
        card.add(Theme.label(title, 18, true, Theme.NAVY), BorderLayout.NORTH);
        card.add(Theme.label(sub,   12, false, Theme.MUTED), BorderLayout.CENTER);
        JButton go = Theme.button("Mở →", Theme.ORANGE);
        go.addActionListener(e -> action.run());
        JPanel wrap = Theme.panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrap.add(go);
        card.add(wrap, BorderLayout.SOUTH);
        return card;
    }

    private void showMenu() {
        setBreadcrumb("Trang chủ", "Menu");
        content.removeAll();

        MenuPanel panel = new MenuPanel(this::onPickDish);
        content.add(panel, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    private void showOrders() {
        setBreadcrumb("Trang chủ", "Đơn của tôi");
        content.removeAll();

        JPanel list = Theme.panel(new GridLayout(0, 1, 0, 8));
        if (orders.isEmpty()) {
            list.add(Theme.label("Bạn chưa có đơn nào.", 13, false, Theme.MUTED));
        } else {
            orders.values().forEach(o -> {
                JPanel row = Theme.card();
                row.setLayout(new BorderLayout());
                row.add(Theme.label("Đơn #" + o.id() + " — Bàn " + o.table(),
                        13, true, Theme.NAVY), BorderLayout.WEST);
                row.add(Theme.label(o.status(), 12, true, Theme.ORANGE_DK),
                        BorderLayout.EAST);
                list.add(row);
            });
        }

        content.add(scroll(list), BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    private void showTableInfo() {
        setBreadcrumb("Trang chủ", "Bàn ăn");
        content.removeAll();
        content.add(Theme.label("Thông tin bàn sẽ hiển thị ở đây.",
                13, false, Theme.MUTED), BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    // ─── Xử lý khi chọn món ───
    private void onPickDish(Menu.Dish dish) {
        // ví dụ: hỏi số lượng rồi gửi
        String qty = JOptionPane.showInputDialog(this,
                "Số lượng cho " + dish.name() + "?", "1");
        if (qty == null || qty.isBlank()) return;
        try {
            int n = Integer.parseInt(qty.trim());
            if (n <= 0) return;
            send(Message.Type.ADD_ITEM, 0, dish.id() + "x" + n);
            JOptionPane.showMessageDialog(this,
                    "Đã thêm " + n + " × " + dish.name() + " vào đơn.",
                    "SAVORY", JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException ex) {
            notice("Số lượng không hợp lệ");
        }
    }

    // ─── Nhận message ───
    @Override protected void onMessage(Message m) {
        if (m.getType() == Message.Type.ORDER_UPDATE) render();
    }
}