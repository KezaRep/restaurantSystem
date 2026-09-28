package client;

import model.*;
import model.Menu;
import ui.BaseFrame;
import ui.Theme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDateTime;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Cashier: real-time active tables, bill, payment and payment history. */
public class CashierClient extends BaseFrame {

    // ═══════════════════════════════════════════
    //  MÀU & STYLE
    // ═══════════════════════════════════════════
    private static final Color ACCENT       = new Color(0x2563EB); // xanh dương
    private static final Color ORANGE       = new Color(0xF97316);
    private static final Color GREEN        = new Color(0x16A34A);
    private static final Color MUTED        = new Color(0x94A3B8);
    private static final Color BORDER       = new Color(0xE2E8F0);
    private static final Color CARD_BG      = Color.WHITE;
    private static final Color ROW_ALT      = new Color(0xF8FAFC);
    private static final Font  FONT         = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font  FONT_BOLD    = new Font("Segoe UI", Font.BOLD, 13);

    // ═══════════════════════════════════════════
    //  STATE
    // ═══════════════════════════════════════════
    private int selectedTable = -1;

    // Map: table -> tổng tiền đang phục vụ (để vẽ card)
    private final Map<Integer, Long> tableTotals = new HashMap<>();
    // Map: table -> danh sách món (để hiển thị khi click card)
    private final Map<Integer, java.util.List<BillLine>> tableBills = new HashMap<>();

    private final JPanel tableGrid = new JPanel(new GridLayout(0, 3, 14, 14));
    private final JComboBox<String> method =
            new JComboBox<>(new String[]{"CASH", "CARD", "TRANSFER"});

    private final JLabel selectedLabel = Theme.label("Chưa chọn bàn", 18, true, Theme.NAVY);
    private final JLabel statusBadge   = Theme.label("—", 11, true, MUTED);
    private final JLabel totalLabel    = Theme.label("0 đ", 22, true, ORANGE);

    // Bảng chi tiết hóa đơn hiện tại
    private final DefaultTableModel billModel = new DefaultTableModel(
            new Object[]{"Món", "SL", "Đơn giá", "Thành tiền"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable billTable = new JTable(billModel);

    // Bảng lịch sử thanh toán
    private final DefaultTableModel historyModel = new DefaultTableModel(
            new Object[]{"Bàn", "Số món", "Tổng tiền", "Ngày giờ thanh toán"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable historyTable = new JTable(historyModel);

    // Lưu chi tiết các món của từng hóa đơn trong lịch sử
    private final java.util.List<java.util.List<BillLine>> historyBills = new ArrayList<>();
    // Lưu dòng lịch sử gốc để rebuild
    private final java.util.List<String[]> historyRows = new ArrayList<>();

    // Panel chi tiết món của hóa đơn lịch sử
    private final JPanel historyDetailPanel = new JPanel(new BorderLayout());
    private final JLabel historyDetailTitle = Theme.label("Chọn một dòng để xem chi tiết",
            14, true, MUTED);

    private static final int TOTAL_TABLES = 30;
    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public CashierClient() { super("Thu ngân", "CASHIER");
        historyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showHistoryDetail(historyTable.getSelectedRow());
        });
        render();
    }

    // ═══════════════════════════════════════════
    //  METADATA
    // ═══════════════════════════════════════════
    @Override protected String[] sideItems() {
        return new String[]{"Bàn đang phục vụ", "Lịch sử hóa đơn", "Cài đặt"};
    }
    @Override protected String subtitle() { return "Thanh toán và đồng bộ bàn thời gian thực"; }
    @Override protected String role() { return "CASHIER"; }

    // ═══════════════════════════════════════════
    //  NAVIGATION
    // ═══════════════════════════════════════════
    @Override protected void navigateTo(String item) {
        if (item.contains("Lịch sử")) send(Message.Type.REQUEST_PAYMENT_HISTORY, 0, "");
        if (item.contains("Cài đặt")) notice("Nhấn ⚙ Cài đặt kết nối ở góc trên bên phải");
        render();
    }

    // ═══════════════════════════════════════════
    //  SNAPSHOT / MESSAGE
    // ═══════════════════════════════════════════
    @Override protected void onSnapshot() {
        rebuildTableTotals();

        if (selectedTable > 0) {
            boolean exists = tableTotals.containsKey(selectedTable);
            if (exists) send(Message.Type.REQUEST_CHECKOUT, selectedTable, "");
            else {
                selectedTable = -1;
                billModel.setRowCount(0);
                totalLabel.setText("0 đ");
                statusBadge.setText("—");
                statusBadge.setForeground(MUTED);
            }
        }
        render();
    }

    @Override protected void onMessage(Message m) {
        switch (m.getType()) {
            case BILL_INFO -> {
                selectedTable = m.getTableNo();
                parseBillIntoTable(m.getContent());
                render();
            }
            case PAYMENT_HISTORY -> {
                parseHistory(m.getContent());
                render();
            }
            case PAYMENT_DONE -> {
                if (m.getTableNo() == selectedTable) {
                    selectedTable = -1; billModel.setRowCount(0);
                    totalLabel.setText("0 đ"); statusBadge.setText("—");
                    statusBadge.setForeground(MUTED);
                }
                send(Message.Type.REQUEST_PAYMENT_HISTORY, 0, "");
                render();
            }
            default -> { }
        }
    }

    // ═══════════════════════════════════════════
    //  RENDER
    // ═══════════════════════════════════════════
    @Override protected void render() {
        content.removeAll();

        if (currentSideItem == 1) {
            content.add(buildHistoryView(), BorderLayout.CENTER);
        } else {
            content.add(buildHeader(), BorderLayout.NORTH);

            JPanel columns = Theme.panel(new BorderLayout(18, 0));
            columns.add(buildTableColumn(), BorderLayout.CENTER);
            columns.add(buildDetailColumn(), BorderLayout.EAST);
            content.add(columns, BorderLayout.CENTER);
        }
        content.revalidate();
        content.repaint();
    }

    // ─── Header ───
    private JPanel buildHeader() {
        JPanel header = Theme.panel(new BorderLayout());

        JPanel left = Theme.panel(new GridLayout(2, 1, 0, 5));
        left.add(Theme.label("SƠ ĐỒ BÀN", 12, true, ORANGE));
        left.add(Theme.label("Nhấn vào bàn đang phục vụ để xem hóa đơn", 22, true, Theme.NAVY));
        header.add(left, BorderLayout.WEST);

        return header;
    }

    // ═══════════════════════════════════════════
    //  CỘT TRÁI — SƠ ĐỒ BÀN
    // ═══════════════════════════════════════════
    private JPanel buildTableColumn() {
        JPanel wrap = Theme.panel(new BorderLayout(0, 12));

        rebuildTableTotals();

        tableGrid.removeAll();
        tableGrid.setOpaque(false);
        for (int t = 1; t <= TOTAL_TABLES; t++) {
            long total = tableTotals.getOrDefault(t, 0L);
            tableGrid.add(tableCard(t, total));
        }

        JPanel gridWrap = Theme.panel(new BorderLayout());
        gridWrap.add(tableGrid, BorderLayout.NORTH);
        wrap.add(scroll(gridWrap), BorderLayout.CENTER);

        return wrap;
    }

    /** Card bo tròn cho từng bàn. Nhấn cả card để chọn. */
    private JPanel tableCard(int table, long total) {
        boolean active = total > 0;
        boolean chosen = table == selectedTable;

        // Card bo tròn
        RoundedPanel card = new RoundedPanel(14, CARD_BG);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(12, 14, 12, 14));
        card.setPreferredSize(new Dimension(0, 118));

        // Viền: chọn -> xanh dương đậm; active -> xám; trống -> xám nhạt
        if (chosen) {
            card.setBorderColor(ACCENT, 2);
        } else if (active) {
            card.setBorderColor(BORDER, 1);
        } else {
            card.setBorderColor(BORDER, 1);
        }

        // ─── Dòng 1: số bàn + badge ───
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel tableLbl = Theme.label(String.format("BÀN %02d", table),
                16, true, active ? Theme.NAVY : MUTED);
        top.add(tableLbl, BorderLayout.WEST);

        JLabel badge = Theme.label(active ? "Đang phục vụ" : "Trống",
                10, true, active ? GREEN : MUTED);
        top.add(badge, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        // ─── Dòng 2: tổng tiền màu cam ───
        JPanel mid = new JPanel(new BorderLayout());
        mid.setOpaque(false);
        JLabel moneyLbl = Theme.label(
                active ? Theme.money(total) : "—",
                20, true, active ? ORANGE : MUTED);
        mid.add(moneyLbl, BorderLayout.WEST);
        card.add(mid, BorderLayout.CENTER);

        // ─── Cursor + click toàn card ───
        if (active) {
            card.setCursor(new Cursor(Cursor.HAND_CURSOR));
            MouseAdapter choose = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    selectedTable = table;
                    send(Message.Type.REQUEST_CHECKOUT, table, "");
                    render();
                }
            };
            for (Component part : new Component[]{card, top, tableLbl, badge, mid, moneyLbl}) {
                part.addMouseListener(choose);
                part.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
        } else {
            card.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }

        return card;
    }

    // ═══════════════════════════════════════════
    //  CỘT PHẢI — CHI TIẾT HÓA ĐƠN
    // ═══════════════════════════════════════════
    private JPanel buildDetailColumn() {
        RoundedPanel right = new RoundedPanel(16, CARD_BG);
        right.setLayout(new BorderLayout(0, 14));
        right.setBorder(new EmptyBorder(16, 16, 16, 16));
        right.setPreferredSize(new Dimension(400, 0));
        right.setBorderColor(BORDER, 1);

        // Header
        JPanel head = new JPanel(new GridLayout(3, 1, 0, 4));
        head.setOpaque(false);
        selectedLabel.setText(selectedTable > 0
                ? String.format("Bàn %02d", selectedTable)
                : "Chưa chọn bàn");
        head.add(selectedLabel);
        head.add(Theme.label("CHI TIẾT HÓA ĐƠN", 11, true, MUTED));
        head.add(statusBadge);
        right.add(head, BorderLayout.NORTH);

        // Bảng món
        styleTable(billTable);
        billTable.getColumnModel().getColumn(0).setPreferredWidth(160);
        billTable.getColumnModel().getColumn(1).setPreferredWidth(40);
        billTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        billTable.getColumnModel().getColumn(3).setPreferredWidth(100);

        JScrollPane sp = new JScrollPane(billTable);
        sp.setBorder(BorderFactory.createLineBorder(BORDER));
        sp.getViewport().setBackground(Color.WHITE);
        right.add(sp, BorderLayout.CENTER);

        right.add(buildDetailActions(), BorderLayout.SOUTH);
        return right;
    }

    private JPanel buildDetailActions() {
        JPanel bottom = new JPanel(new GridLayout(0, 1, 0, 9));
        bottom.setOpaque(false);

        // ─── TỔNG CỘNG: label cam trên, số tiền cam dưới ───
        JPanel totalBlock = new JPanel(new GridLayout(2, 1, 0, 2));
        totalBlock.setOpaque(false);
        totalBlock.add(Theme.label("TỔNG CỘNG", 12, true, ORANGE));

        JPanel totalRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        totalRow.setOpaque(false);
        totalRow.add(totalLabel);
        totalBlock.add(totalRow);
        bottom.add(totalBlock);

        // Phương thức thanh toán
        bottom.add(Theme.label("Phương thức thanh toán", 12, true, MUTED));
        method.setFont(FONT);
        method.setBackground(Color.WHITE);
        bottom.add(method);

        // ─── 2 nút cạnh nhau: Thêm món (trái) | Xác nhận (phải) ───
        JPanel btnRow = new JPanel(new GridLayout(1, 2, 10, 0));
        btnRow.setOpaque(false);

        RoundedButton add = new RoundedButton("+ Thêm món", GREEN, 12);
        add.addActionListener(e -> addItem());
        btnRow.add(add);

        RoundedButton pay = new RoundedButton("✓ Xác nhận", ORANGE, 12);
        pay.addActionListener(e -> {
            if (selectedTable < 1) { notice("Chọn bàn trước"); return; }
            if (JOptionPane.showConfirmDialog(this,
                    "Thanh toán bàn " + selectedTable + "?",
                    "Xác nhận", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                send(Message.Type.CONFIRM_PAYMENT, selectedTable,
                        (String) method.getSelectedItem());
            }
        });
        btnRow.add(pay);

        bottom.add(btnRow);
        return bottom;
    }

    // ═══════════════════════════════════════════
    //  LỊCH SỬ HÓA ĐƠN
    // ═══════════════════════════════════════════
    private JPanel buildHistoryView() {
        JPanel wrap = Theme.panel(new BorderLayout(0, 14));

        JPanel head = Theme.panel(new GridLayout(2, 1, 0, 5));
        head.add(Theme.label("LỊCH SỬ HÓA ĐƠN", 12, true, ORANGE));
        head.add(Theme.label("Toàn bộ giao dịch đã thanh toán", 20, true, Theme.NAVY));
        wrap.add(head, BorderLayout.NORTH);

        // Chia đôi: bảng trên — panel chi tiết dưới
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setResizeWeight(0.55);
        split.setDividerSize(8);
        split.setBorder(null);
        split.setOpaque(false);

        // ─── Bảng lịch sử ───
        RoundedPanel tableCard = new RoundedPanel(14, CARD_BG);
        tableCard.setLayout(new BorderLayout());
        tableCard.setBorder(new EmptyBorder(10, 10, 10, 10));
        tableCard.setBorderColor(BORDER, 1);

        styleTable(historyTable);
        historyTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(70);
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        historyTable.getColumnModel().getColumn(3).setPreferredWidth(180);


        JScrollPane sp = new JScrollPane(historyTable);
        sp.setBorder(BorderFactory.createLineBorder(BORDER));
        sp.getViewport().setBackground(Color.WHITE);
        tableCard.add(sp, BorderLayout.CENTER);
        split.setTopComponent(tableCard);

        // ─── Panel chi tiết món của hóa đơn đã chọn ───
        RoundedPanel detailCard = new RoundedPanel(14, CARD_BG);
        detailCard.setLayout(new BorderLayout(0, 10));
        detailCard.setBorder(new EmptyBorder(14, 14, 14, 14));
        detailCard.setBorderColor(BORDER, 1);

        historyDetailTitle.setText("Chọn một dòng để xem chi tiết");
        historyDetailTitle.setForeground(MUTED);

        JPanel detailHead = new JPanel(new BorderLayout());
        detailHead.setOpaque(false);
        detailHead.add(Theme.label("CHI TIẾT MÓN ĐÃ THANH TOÁN", 11, true, MUTED),
                BorderLayout.NORTH);
        detailHead.add(historyDetailTitle, BorderLayout.SOUTH);
        detailCard.add(detailHead, BorderLayout.NORTH);

        historyDetailPanel.setOpaque(false);
        detailCard.add(historyDetailPanel, BorderLayout.CENTER);
        split.setBottomComponent(detailCard);

        wrap.add(split, BorderLayout.CENTER);
        return wrap;
    }

    /** Khi click 1 dòng lịch sử -> hiện panel tất cả món đã thanh toán. */
    private void showHistoryDetail(int row) {
        historyDetailPanel.removeAll();

        if (row < 0 || row >= historyBills.size()) {
            historyDetailTitle.setText("Chọn một dòng để xem chi tiết");
            historyDetailTitle.setForeground(MUTED);
            historyDetailPanel.revalidate();
            historyDetailPanel.repaint();
            return;
        }

        String[] meta = historyRows.get(row);
        historyDetailTitle.setText(String.format(
                "Bàn %s  •  %s  •  Tổng: %s",
                meta[0], meta[3], meta[2]));
        historyDetailTitle.setForeground(ORANGE);

        // Bảng món của hóa đơn đó
        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"Món", "SL", "Đơn giá", "Thành tiền"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (BillLine bl : historyBills.get(row)) {
            model.addRow(new Object[]{
                    bl.name, bl.qty, Theme.money(bl.price), Theme.money(bl.total)});
        }

        JTable t = new JTable(model);
        styleTable(t);
        t.getColumnModel().getColumn(0).setPreferredWidth(200);
        t.getColumnModel().getColumn(1).setPreferredWidth(50);
        t.getColumnModel().getColumn(2).setPreferredWidth(100);
        t.getColumnModel().getColumn(3).setPreferredWidth(110);

        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(BorderFactory.createLineBorder(BORDER));
        sp.getViewport().setBackground(Color.WHITE);
        historyDetailPanel.add(sp, BorderLayout.CENTER);

        historyDetailPanel.revalidate();
        historyDetailPanel.repaint();
    }

    // ═══════════════════════════════════════════
    //  TIỆN ÍCH
    // ═══════════════════════════════════════════
    private void styleTable(JTable t) {
        t.setFont(FONT);
        t.setRowHeight(32);
        t.setForeground(Theme.NAVY);
        t.setBackground(Color.WHITE);
        t.setGridColor(new Color(0xEEF2F7));
        t.setShowVerticalLines(false);
        t.setSelectionBackground(new Color(0xDBEAFE));
        t.setSelectionForeground(Theme.NAVY);
        t.setFillsViewportHeight(true);

        JTableHeader th = t.getTableHeader();
        th.setFont(FONT_BOLD);
        th.setBackground(new Color(0xF1F5F9));
        th.setForeground(MUTED);
        th.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        th.setPreferredSize(new Dimension(0, 34));

        // Zebra + căn phải cột số
        DefaultTableCellRenderer right = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object v,
                    boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(table, v, sel, foc, r, c);
                setHorizontalAlignment(SwingConstants.RIGHT);
                if (!sel) comp.setBackground(r % 2 == 0 ? Color.WHITE : ROW_ALT);
                return comp;
            }
        };
        for (int i = 1; i < t.getColumnCount(); i++) {
            t.getColumnModel().getColumn(i).setCellRenderer(right);
        }

        DefaultTableCellRenderer left = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object v,
                    boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(table, v, sel, foc, r, c);
                if (!sel) comp.setBackground(r % 2 == 0 ? Color.WHITE : ROW_ALT);
                return comp;
            }
        };
        t.getColumnModel().getColumn(0).setCellRenderer(left);
    }

    private void rebuildTableTotals() {
        tableTotals.clear();
        tableBills.clear();
        for (Protocol.Order o : orders.values()) {
            Menu.Dish d = Menu.get(o.dish());
            if (d == null) continue;
            long add = (long) d.price() * o.qty();
            tableTotals.put(o.table(), tableTotals.getOrDefault(o.table(), 0L) + add);
            tableBills.computeIfAbsent(o.table(), k -> new ArrayList<>())
                    .add(new BillLine(d.name(), o.qty(), d.price(), add));
        }
    }

    /** Parse BILL_INFO: mỗi dòng "Tên\tSL\tĐơnGiá\tThànhTiền"; cuối "TOTAL\t<n>". */
    private void parseBillIntoTable(String data) {
        billModel.setRowCount(0);
        long total = 0;
        boolean hasItem = false;

        for (String line : data.split("\n")) {
            if (line.isBlank()) continue;
            if (line.startsWith("TOTAL\t")) {
                try { total = Long.parseLong(line.substring(6).trim()); }
                catch (NumberFormatException ignored) {}
                continue;
            }
            String[] p = line.split("\t");
            if (p.length >= 4) {
                try {
                    billModel.addRow(new Object[]{
                            p[0], p[1], Theme.money(Long.parseLong(p[2])),
                            Theme.money(Long.parseLong(p[3]))});
                    hasItem = true;
                } catch (NumberFormatException ignored) {
                    billModel.addRow(new Object[]{p[0], p[1], p[2], p[3]});
                    hasItem = true;
                }
            } else {
                billModel.addRow(new Object[]{line, "", "", ""});
                hasItem = true;
            }
        }

        totalLabel.setText(Theme.money(total));
        totalLabel.setForeground(ORANGE);
        statusBadge.setText(hasItem ? "Có món — chờ thanh toán" : "Bàn trống");
        statusBadge.setForeground(hasItem ? ORANGE : MUTED);
    }

    /** History is sent by the server with persisted payment timestamp and line items. */
    private void parseHistory(String data) {
        historyModel.setRowCount(0);
        historyRows.clear(); historyBills.clear();
        java.util.List<BillLine> current = null;
        if (data == null || data.isBlank()) return;
        for (String line : data.split("\n")) {
            String[] x = line.split("\t", -1);
            try {
                if (x[0].equals("H") && x.length >= 6) {
                    int table = Integer.parseInt(x[2]);
                    long amount = new java.math.BigDecimal(x[3]).longValueExact();
                    long epoch = Long.parseLong(x[4]);
                    String time = epoch > 0 ? new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                            .format(new Date(epoch)) : "Không rõ";
                    String money = Theme.money(amount);
                    current = new ArrayList<>(); historyBills.add(current);
                    historyRows.add(new String[]{String.valueOf(table), "0", money, time});
                    historyModel.addRow(new Object[]{String.format("Bàn %02d", table), 0, money, time});
                } else if (x[0].equals("I") && x.length >= 5 && current != null) {
                    current.add(new BillLine(Protocol.decode(x[1]),Integer.parseInt(x[2]),
                        new java.math.BigDecimal(x[3]).longValueExact(),
                        new java.math.BigDecimal(x[4]).longValueExact()));
                    int row = historyModel.getRowCount()-1;
                    int qty = current.stream().mapToInt(BillLine::qty).sum();
                    historyModel.setValueAt(qty,row,1);
                    historyRows.get(row)[1]=String.valueOf(qty);
                } else if (x[0].equals("E")) current = null;
            } catch (RuntimeException ignored) { /* Ignore malformed records. */ }
        }
    }

    private void addItem() {
        if (selectedTable < 1 || !tableTotals.containsKey(selectedTable)) {
            notice("Vui lòng chọn bàn đang phục vụ"); return;
        }
        DefaultTableModel menuModel = new DefaultTableModel(
                new Object[]{"Món ăn", "Đơn giá", "Số lượng"},0) {
            @Override public boolean isCellEditable(int r,int c){return c==2;}
            @Override public Class<?> getColumnClass(int c){return c==2?Integer.class:String.class;}
        };
        for (Menu.Dish d: Menu.ITEMS) menuModel.addRow(new Object[]{d.name(),Theme.money(d.price()),0});
        JTable menu = new JTable(menuModel); styleTable(menu);
        menu.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(new JTextField()));
        JTextField notes = new JTextField();
        JPanel form = new JPanel(new BorderLayout(0,8));
        form.add(new JScrollPane(menu),BorderLayout.CENTER);
        JPanel foot=new JPanel(new BorderLayout(8,0));
        foot.add(new JLabel("Ghi chú cho bếp:"),BorderLayout.WEST);
        foot.add(notes,BorderLayout.CENTER); form.add(foot,BorderLayout.SOUTH);
        form.setPreferredSize(new Dimension(580,390));
        if(JOptionPane.showConfirmDialog(this,form,"Thêm món - Bàn " + selectedTable,
                JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
        if(menu.isEditing())menu.getCellEditor().stopCellEditing();
        java.util.List<String> selected=new ArrayList<>();
        try {
            for(int i=0;i<menuModel.getRowCount();i++){
                String raw=String.valueOf(menuModel.getValueAt(i,2)).trim();
                int qty=raw.isEmpty()?0:Integer.parseInt(raw);
                if(qty<0||qty>30)throw new NumberFormatException();
                if(qty>0)selected.add(Menu.ITEMS.get(i).id()+"\t"+qty+"\t"+Protocol.encode(notes.getText()));
            }
        }catch(NumberFormatException ex){notice("Số lượng phải từ 0 đến 30");return;}
        if(selected.isEmpty()){notice("Chưa chọn món nào");return;}
        for(String item:selected)send(Message.Type.ADD_ITEM,selectedTable,item);
        // Snapshot broadcast from server will refresh the selected table and kitchen.
    }

    // ═══════════════════════════════════════════
    //  INNER CLASSES
    // ═══════════════════════════════════════════
    private record BillLine(String name, int qty, long price, long total) {}

    /** Panel bo tròn có viền màu tùy chọn. */
    private static class RoundedPanel extends JPanel {
        private final int radius;
        private Color bg;
        private Color borderColor = new Color(0xE2E8F0);
        private int borderWidth = 1;

        RoundedPanel(int radius, Color bg) {
            this.radius = radius;
            this.bg = bg;
            setOpaque(false);
        }

        void setBorderColor(Color c, int w) { this.borderColor = c; this.borderWidth = w; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1,
                    radius, radius));
            g2.dispose();
            super.paintComponent(g);
        }

        @Override protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(borderWidth));
            g2.draw(new RoundRectangle2D.Double(borderWidth / 2.0, borderWidth / 2.0,
                    getWidth() - borderWidth, getHeight() - borderWidth, radius, radius));
            g2.dispose();
        }
    }

    /** Nút bo tròn. */
    private static class RoundedButton extends JButton {
        private final Color bg;
        private final int radius;

        RoundedButton(String text, Color bg, int radius) {
            super(text);
            this.bg = bg;
            this.radius = radius;
            setFont(new Font("Segoe UI", Font.BOLD, 13));
            setForeground(Color.WHITE);
            setFocusPainted(false);
            setBorder(new EmptyBorder(9, 12, 9, 12));
            setContentAreaFilled(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getModel().isRollover() ? bg.darker() : bg);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), radius, radius));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ═══════════════════════════════════════════
    //  ENTRY
    // ═══════════════════════════════════════════
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CashierClient().setVisible(true));
    }
}