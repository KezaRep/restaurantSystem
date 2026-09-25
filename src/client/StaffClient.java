package client;

import model.*;
import model.Menu;
import ui.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;

public class StaffClient extends BaseFrame {

    // ═══════════════════════════════════════════
    //  STATE
    // ═══════════════════════════════════════════
    private int selectedTable = 1;

    private final JLabel totalLabel   = Theme.label("0 đ", 23, true, Theme.ORANGE);
    private final JPanel menuGrid     = Theme.panel(new GridLayout(0, 3, 14, 14));
    private final JPanel orderList    = Theme.panel(new GridLayout(0, 1, 5, 5));
    private final JLabel selectedLabel = Theme.label("Bàn 01", 20, true, Theme.NAVY);
    private final JComboBox<Integer> tableBox = new JComboBox<>();
    private final Map<String, Integer> cart = new LinkedHashMap<>();
    private final JTextField note = Theme.field("", 18);
    private final JLabel alert = Theme.label("Chọn món để tạo đơn mới", 12, false, Theme.MUTED);

    // ═══════════════════════════════════════════
    //  KHỞI TẠO
    // ═══════════════════════════════════════════
    public StaffClient() {
        super("Bảng điều khiển phục vụ", "STAFF");

        for (int i = 1; i <= 30; i++) tableBox.addItem(i);
        tableBox.addActionListener(e -> {
            selectedTable = (Integer) tableBox.getSelectedItem();
            render();
        });

        render();
    }

    // ═══════════════════════════════════════════
    //  METADATA
    // ═══════════════════════════════════════════
    @Override
    protected String[] sideItems() {
        return new String[]{"Tổng quan", "Thực đơn & đặt món", "Quản lý bàn", "Thanh toán"};
    }

    @Override
    protected String subtitle() {
        return "Đặt món • theo dõi bếp • thanh toán thời gian thực";
    }

    @Override
    protected String role() { return "STAFF"; }

    // ═══════════════════════════════════════════
    //  NHẬN MESSAGE
    // ═══════════════════════════════════════════
    @Override
    protected void onMessage(Message m) {
        switch (m.getType()) {
            case SERVE_NOTIFY -> {
                Protocol.Order o = Protocol.parse(m.getContent());
                Menu.Dish d = Menu.get(o.dish());
                alert.setText("✓ Bàn " + o.table() + ": "
                        + (d == null ? o.dish() : d.name())
                        + " đã sẵn sàng phục vụ!");
                java.awt.Toolkit.getDefaultToolkit().beep();
            }
            case ORDER_ACK -> alert.setText("✓ " + m.getContent());
            case BILL_INFO -> showBill(m.getTableNo(), m.getContent());
            case PAYMENT_DONE -> alert.setText("✓ " + m.getContent());
            default -> { }
        }
    }

    // ═══════════════════════════════════════════
    //  RENDER
    // ═══════════════════════════════════════════
    @Override
    protected void render() {
        content.removeAll();

        content.add(buildHeader(), BorderLayout.NORTH);

        JPanel columns = Theme.panel(new BorderLayout(18, 0));
        columns.add(buildMenuColumn(), BorderLayout.CENTER);
        columns.add(buildCartColumn(), BorderLayout.EAST);
        content.add(columns, BorderLayout.CENTER);

        content.revalidate();
        content.repaint();
    }

    // ─── Header ───
    private JPanel buildHeader() {
        JPanel header = Theme.panel(new BorderLayout());

        JPanel left = Theme.panel(new GridLayout(2, 1, 0, 5));
        left.add(Theme.label("THỰC ĐƠN HÔM NAY", 12, true, Theme.ORANGE));
        left.add(Theme.label("Hương vị dành riêng cho bạn", 22, true, Theme.NAVY));
        header.add(left, BorderLayout.WEST);

        JPanel select = Theme.panel(new FlowLayout(FlowLayout.RIGHT));
        select.add(Theme.label("Đang phục vụ", 13, false, Theme.MUTED));
        tableBox.setSelectedItem(selectedTable);
        select.add(tableBox);
        header.add(select, BorderLayout.EAST);

        return header;
    }

    // ─── Cột trái: menu ───
    private JPanel buildMenuColumn() {
        JPanel menus = Theme.panel(new BorderLayout(0, 14));

        menuGrid.removeAll();
        for (Menu.Dish d : Menu.ITEMS) menuGrid.add(dishCard(d));

        JPanel menuWrap = Theme.panel(new BorderLayout());
        menuWrap.add(menuGrid, BorderLayout.NORTH);
        menus.add(scroll(menuWrap), BorderLayout.CENTER);

        return menus;
    }

    // ─── Cột phải: giỏ hàng + thanh toán ───
    private JPanel buildCartColumn() {
        JPanel right = Theme.card(new BorderLayout(0, 14));
        right.setPreferredSize(new Dimension(330, 0));

        right.add(buildCartHeader(), BorderLayout.NORTH);
        right.add(buildCartList(), BorderLayout.CENTER);
        right.add(buildCartBottom(), BorderLayout.SOUTH);

        return right;
    }

    private JPanel buildCartHeader() {
        JPanel rtop = Theme.panel(new GridLayout(3, 1, 0, 5));
        selectedLabel.setText(String.format("Bàn %02d", selectedTable));
        rtop.add(selectedLabel);
        rtop.add(Theme.label("GIỎ HÀNG / ĐƠN HIỆN TẠI", 11, true, Theme.MUTED));
        rtop.add(alert);
        return rtop;
    }

    private JScrollPane buildCartList() {
        orderList.removeAll();
        long total = 0;

        // Món trong giỏ chưa gửi
        for (var entry : cart.entrySet()) {
            Menu.Dish d = Menu.get(entry.getKey());
            if (d != null) {
                total += (long) d.price() * entry.getValue();
                orderList.add(cartLine(
                        d.name() + " x" + entry.getValue(),
                        Theme.money((long) d.price() * entry.getValue()),
                        () -> { cart.remove(d.id()); render(); }));
            }
        }

        // Món đã gửi bếp của bàn hiện tại
        for (Protocol.Order o : orders.values()) {
            if (o.table() == selectedTable) {
                Menu.Dish d = Menu.get(o.dish());
                if (d != null) {
                    total += (long) d.price() * o.qty();
                    orderList.add(cartLine(
                            d.name() + " x" + o.qty(),
                            status(o.status()),
                            null));
                }
            }
        }

        cartTotal = total;

        JPanel listWrap = Theme.panel(new BorderLayout());
        listWrap.add(orderList, BorderLayout.NORTH);
        return scroll(listWrap);
    }

    private long cartTotal = 0;   // lưu tạm để bottom dùng

    private JPanel buildCartBottom() {
        JPanel bottom = Theme.panel(new GridLayout(0, 1, 0, 9));

        bottom.add(Theme.label("Ghi chú cho bếp", 12, true, Theme.MUTED));
        bottom.add(note);

        totalLabel.setText(Theme.money(cartTotal));
        bottom.add(totalLabel);

        // Nút gửi đơn
        JButton place = Theme.button("Gửi đơn đến bếp  →", Theme.ORANGE);
        place.addActionListener(e -> {
            if (cart.isEmpty()) {
                notice("Vui lòng chọn món trước");
                return;
            }
            for (var entry : new LinkedHashMap<>(cart).entrySet()) {
                send(Message.Type.CREATE_ORDER, selectedTable,
                        entry.getKey() + "\t" + entry.getValue()
                        + "\t" + Protocol.encode(note.getText()));
            }
            cart.clear();
            note.setText("");
            render();
        });
        bottom.add(place);

        // Nút xem hoá đơn
        JButton bill = Theme.button("Xem hóa đơn & thanh toán", Theme.NAVY);
        bill.addActionListener(e ->
                send(Message.Type.REQUEST_CHECKOUT, selectedTable, ""));
        bottom.add(bill);

        return bottom;
    }

    // ═══════════════════════════════════════════
    //  TIỆN ÍCH
    // ═══════════════════════════════════════════
    private String status(String s) {
        return switch (s) {
            case "NEW"     -> "Chờ bếp";
            case "COOKING" -> "Đang chế biến";
            case "READY"   -> "✓ Sẵn sàng";
            default        -> s;
        };
    }

    private JPanel cartLine(String name, String value, Runnable remove) {
        JPanel p = Theme.panel(new BorderLayout());
        p.setBorder(new EmptyBorder(8, 0, 8, 0));
        p.add(Theme.label(name, 12, true, Theme.NAVY), BorderLayout.CENTER);

        JPanel r = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        r.add(Theme.label(value, 11, false, Theme.MUTED));

        if (remove != null) {
            JButton b = new JButton("×");
            b.addActionListener(e -> remove.run());
            r.add(b);
        }
        p.add(r, BorderLayout.EAST);

        return p;
    }

    private JPanel dishCard(Menu.Dish d) {
        JPanel p = Theme.card(new BorderLayout(0, 8));
        p.add(new FoodArt(d.icon()), BorderLayout.NORTH);

        JPanel info = Theme.panel(new GridLayout(3, 1, 0, 3));
        info.add(Theme.label(d.category().toUpperCase(), 10, true, Theme.ORANGE));
        info.add(Theme.label(d.name(), 15, true, Theme.NAVY));
        info.add(Theme.label(d.description(), 11, false, Theme.MUTED));
        p.add(info, BorderLayout.CENTER);

        JPanel bottom = Theme.panel(new BorderLayout());
        bottom.add(Theme.label(Theme.money(d.price()), 14, true, Theme.NAVY),
                BorderLayout.WEST);

        JButton add = Theme.button("+ Thêm", Theme.GREEN);
        add.addActionListener(e -> {
            cart.merge(d.id(), 1, Integer::sum);
            render();
        });
        bottom.add(add, BorderLayout.EAST);

        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private void showBill(int table, String data) {
        String[] lines = data.split("\n");
        long total = 0;
        StringBuilder b = new StringBuilder(
                "SAVORY RESTAURANT\n------------------------------\nBÀN " + table + "\n\n");

        for (String line : lines) {
            if (line.startsWith("TOTAL\t")) {
                total = Long.parseLong(line.substring(6));
            } else {
                b.append(line).append(" đ\n");
            }
        }
        b.append("\nTỔNG THANH TOÁN: ").append(Theme.money(total));

        Object[] options = {"Xác nhận đã thu tiền", "Đóng"};
        int choice = JOptionPane.showOptionDialog(
                this, b.toString(), "HÓA ĐƠN",
                JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[1]);

        if (choice == 0) send(Message.Type.CONFIRM_PAYMENT, table, "");
    }

    // ═══════════════════════════════════════════
    //  ENTRY
    // ═══════════════════════════════════════════
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new StaffClient().setVisible(true));
    }
}