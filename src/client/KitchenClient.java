package client;

import model.*;
import model.Menu;
import ui.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class KitchenClient extends BaseFrame {

    // ═══════════════════════════════════════════
    //  STATE
    // ═══════════════════════════════════════════
    private final JPanel grid = Theme.panel(new GridLayout(0, 3, 16, 16));
    private final JLabel stats = Theme.label("0 đơn đang xử lý", 13, false, Theme.MUTED);

    // ═══════════════════════════════════════════
    //  KHỞI TẠO
    // ═══════════════════════════════════════════
    public KitchenClient() {
        super("Kitchen Display System", "KITCHEN");
        render();
    }

    // ═══════════════════════════════════════════
    //  METADATA
    // ═══════════════════════════════════════════
    @Override
    protected String[] sideItems() {
        return new String[]{"Màn hình bếp", "Đơn mới", "Đang chế biến", "Sẵn sàng"};
    }

    @Override
    protected String subtitle() {
        return "Quản lý phiếu bếp • cập nhật tức thời";
    }

    @Override
    protected String role() { return "KITCHEN"; }

    // ═══════════════════════════════════════════
    //  NHẬN MESSAGE
    // ═══════════════════════════════════════════
    @Override
    protected void onMessage(Message m) {
        if (m.getType() == Message.Type.NEW_ORDER_NOTIFY) {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    // ═══════════════════════════════════════════
    //  RENDER
    // ═══════════════════════════════════════════
    @Override
    protected void render() {
        content.removeAll();

        content.add(buildHeader(), BorderLayout.NORTH);

        // ─── Grid đơn ───
        grid.removeAll();

        java.util.List<Protocol.Order> sorted = new ArrayList<>(orders.values());
        sorted.sort(
                Comparator.comparingInt((Protocol.Order o) ->
                        o.status().equals("NEW") ? 0
                                : o.status().equals("COOKING") ? 1 : 2)
                        .thenComparingLong(Protocol.Order::created));

        for (Protocol.Order o : sorted) grid.add(orderCard(o));

        if (sorted.isEmpty()) {
            grid.add(Theme.label(
                    "Chưa có đơn nào. Đơn mới sẽ xuất hiện tự động.",
                    17, false, Theme.MUTED));
        }

        JPanel wrap = Theme.panel(new BorderLayout());
        wrap.add(grid, BorderLayout.NORTH);
        content.add(scroll(wrap), BorderLayout.CENTER);

        content.revalidate();
        content.repaint();
    }

    // ─── Header ───
    private JPanel buildHeader() {
        JPanel top = Theme.panel(new BorderLayout());

        JPanel t = Theme.panel(new GridLayout(2, 1, 0, 6));
        t.add(Theme.label("BẾP ĐANG HOẠT ĐỘNG", 12, true, Theme.ORANGE));
        t.add(Theme.label("Đơn hàng cần xử lý", 23, true, Theme.NAVY));
        top.add(t, BorderLayout.WEST);

        long pending = orders.values().stream()
                .filter(o -> !o.status().equals("READY"))
                .count();
        stats.setText(pending + " đơn đang xử lý  •  " + orders.size() + " tổng đơn");
        top.add(stats, BorderLayout.EAST);

        return top;
    }

    // ═══════════════════════════════════════════
    //  CARD ĐƠN
    // ═══════════════════════════════════════════
    private JPanel orderCard(Protocol.Order o) {
        Menu.Dish d = Menu.get(o.dish());
        JPanel p = Theme.card(new BorderLayout(0, 10));

        p.add(buildCardHeader(o), BorderLayout.NORTH);
        p.add(buildCardBody(o, d), BorderLayout.CENTER);
        p.add(buildCardAction(o), BorderLayout.SOUTH);

        return p;
    }

    private JPanel buildCardHeader(Protocol.Order o) {
        JPanel h = Theme.panel(new BorderLayout());

        h.add(Theme.label(
                String.format("BÀN %02d", o.table()),
                20, true, Theme.NAVY),
                BorderLayout.WEST);

        String state = switch (o.status()) {
            case "NEW"     -> "● ĐƠN MỚI";
            case "COOKING" -> "◉ ĐANG NẤU";
            default        -> "✓ HOÀN THÀNH";
        };
        h.add(Theme.label(state, 11, true,
                o.status().equals("READY") ? Theme.GREEN : Theme.ORANGE),
                BorderLayout.EAST);

        return h;
    }

    private JPanel buildCardBody(Protocol.Order o, Menu.Dish d) {
        JPanel center = Theme.panel(new BorderLayout(0, 8));
        center.add(new FoodArt(d == null ? "RICE" : d.icon()), BorderLayout.NORTH);

        JPanel labels = Theme.panel(new GridLayout(0, 1, 0, 7));
        labels.add(Theme.label(d == null ? o.dish() : d.name(), 17, true, Theme.NAVY));
        labels.add(Theme.label("Số lượng: " + o.qty() + "   •   #" + o.id(),
                12, false, Theme.MUTED));
        labels.add(Theme.label("Lúc: " + new SimpleDateFormat("HH:mm:ss")
                .format(new Date(o.created())), 12, false, Theme.MUTED));

        if (!o.note().isBlank()) {
            labels.add(Theme.label("Ghi chú: " + o.note(), 12, false, Theme.ORANGE));
        }

        center.add(labels, BorderLayout.CENTER);
        return center;
    }

    private JButton buildCardAction(Protocol.Order o) {
//        if (o.status().equals("READY")) {
//            return Theme.label("Đã báo nhân viên phục vụ", 12, true, Theme.GREEN);
//        }

        boolean isNew = o.status().equals("NEW");
        String next = isNew ? "COOKING" : "READY";

        JButton action = Theme.button(
                isNew ? "Bắt đầu chế biến  →" : "✓ Hoàn thành & báo phục vụ",
                isNew ? Theme.ORANGE : Theme.GREEN);

        action.addActionListener(e ->
                send(Message.Type.UPDATE_COOK_STATUS, o.table(), o.id() + "\t" + next));

        return action;
    }

    // ═══════════════════════════════════════════
    //  ENTRY
    // ═══════════════════════════════════════════
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new KitchenClient().setVisible(true));
    }
}