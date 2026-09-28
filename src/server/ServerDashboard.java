package server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.time.LocalTime;

/** Swing dashboard; all client sockets are handled by RestaurantServer. */
public class ServerDashboard extends JFrame {

    // ===== Bảng màu phong cách web sáng =====
    private static final Color BG          = new Color(0xF5F7FA); // nền tổng
    private static final Color CARD        = Color.WHITE;         // nền card
    private static final Color BORDER      = new Color(0xE2E8F0); // viền nhạt
    private static final Color TEXT        = new Color(0x1E293B); // chữ chính
    private static final Color MUTED       = new Color(0x64748B); // chữ phụ
    private static final Color PRIMARY     = new Color(0x2563EB); // xanh dương
    private static final Color SUCCESS     = new Color(0x16A34A); // xanh lá
    private static final Color DANGER      = new Color(0xDC2626); // đỏ
    private static final Color HEADER_BG   = new Color(0x1E293B); // header tối
    private static final Font  FONT        = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font  FONT_BOLD   = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font  FONT_TITLE  = new Font("Segoe UI", Font.BOLD, 15);

    private RestaurantServer server;

    private final JTextField port = new JTextField("5001", 7);
    private final JTextArea  activity = new JTextArea();

    // Bảng nhân viên chuyên nghiệp
    private final DefaultTableModel employeeModel =
            new DefaultTableModel(new Object[]{"Nhân viên", "Chức năng", "Trạng thái"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
    private final JTable employeeTable = new JTable(employeeModel);

    private final JButton start = new JButton("START");
    private final JButton stop  = new JButton("STOP");

    public ServerDashboard() {
        super("Server Dashboard");
        setSize(950, 620);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(BG);

        // ===== HEADER =====
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HEADER_BG);
        header.setBorder(new EmptyBorder(14, 20, 14, 20));

        JLabel title = new JLabel("Restaurant Server Dashboard");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controls.setOpaque(false);

        JLabel portLbl = new JLabel("TCP PORT");
        portLbl.setForeground(new Color(0xCBD5E1));
        portLbl.setFont(FONT_BOLD);

        styleInput(port);
        styleButton(start, SUCCESS);
        styleButton(stop, DANGER);
        stop.setEnabled(false);

        controls.add(portLbl);
        controls.add(port);
        controls.add(start);
        controls.add(stop);

        header.add(title, BorderLayout.WEST);
        header.add(controls, BorderLayout.EAST);

        // ===== BODY =====
        JPanel body = new JPanel(new GridLayout(1, 2, 16, 0));
        body.setBackground(BG);
        body.setBorder(new EmptyBorder(16, 16, 16, 16));

        // --- Card: Nhân viên ---
        JPanel leftCard = createCard("NHÂN VIÊN ĐANG KẾT NỐI");

        employeeTable.setFont(FONT);
        employeeTable.setRowHeight(34);
        employeeTable.setForeground(TEXT);
        employeeTable.setBackground(CARD);
        employeeTable.setGridColor(BORDER);
        employeeTable.setShowVerticalLines(false);
        employeeTable.setSelectionBackground(new Color(0xDBEAFE));
        employeeTable.setSelectionForeground(TEXT);
        employeeTable.setFillsViewportHeight(true);

        JTableHeader th = employeeTable.getTableHeader();
        th.setFont(FONT_BOLD);
        th.setBackground(new Color(0xF1F5F9));
        th.setForeground(MUTED);
        th.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        th.setPreferredSize(new Dimension(0, 34));

        // Cột "Trạng thái" hiển thị badge màu
        employeeTable.getColumnModel().getColumn(2).setCellRenderer(new StatusBadgeRenderer());

        JScrollPane tableScroll = new JScrollPane(employeeTable);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        tableScroll.getViewport().setBackground(CARD);

        leftCard.add(tableScroll, BorderLayout.CENTER);

        // --- Card: Activity ---
        JPanel rightCard = createCard("SERVER ACTIVITY");

        activity.setEditable(false);
        activity.setFont(new Font("Consolas", Font.PLAIN, 12));
        activity.setBackground(new Color(0xFAFAFA));
        activity.setForeground(TEXT);
        activity.setBorder(new EmptyBorder(10, 12, 10, 12));
        activity.setLineWrap(true);
        activity.setWrapStyleWord(true);

        JScrollPane logScroll = new JScrollPane(activity);
        logScroll.setBorder(BorderFactory.createEmptyBorder());
        logScroll.getViewport().setBackground(new Color(0xFAFAFA));

        rightCard.add(logScroll, BorderLayout.CENTER);

        body.add(leftCard);
        body.add(rightCard);

        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);

        // ===== Sự kiện =====
        start.addActionListener(e -> {
            try {
                int p = Integer.parseInt(port.getText().trim());
                if (p < 1 || p > 65535) throw new NumberFormatException();
                server = new RestaurantServer(p);
                server.onActivity(this::append);
                start.setEnabled(false);
                stop.setEnabled(true);
                new Thread(() -> {
                    try { server.start(); }
                    catch (Exception ex) { append("ERROR: " + ex.getMessage()); }
                    finally {
                        SwingUtilities.invokeLater(() -> {
                            start.setEnabled(true);
                            stop.setEnabled(false);
                        });
                    }
                }, "restaurant-server").start();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Cổng TCP không hợp lệ");
            }
        });

        stop.addActionListener(e -> {
            if (server != null) server.stop();
            stop.setEnabled(false);
            start.setEnabled(true);
            append("SERVER STOPPED");
        });

        // Refresh bảng nhân viên mỗi giây
        new javax.swing.Timer(1000, e -> refreshEmployees()).start();
    }

    // ===== Helpers UI =====
    private JPanel createCard(String titleText) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(0, 0, 0, 0)
        ));

        JLabel titleLbl = new JLabel(titleText);
        titleLbl.setFont(FONT_TITLE);
        titleLbl.setForeground(TEXT);
        titleLbl.setBorder(new EmptyBorder(14, 16, 12, 16));

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(CARD);
        titleBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        titleBar.add(titleLbl, BorderLayout.WEST);

        card.add(titleBar, BorderLayout.NORTH);
        return card;
    }

    private void styleButton(JButton b, Color bg) {
        b.setFont(FONT_BOLD);
        b.setForeground(Color.WHITE);
        b.setBackground(bg);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setBorder(new EmptyBorder(8, 18, 8, 18));
    }

    private void styleInput(JTextField f) {
        f.setFont(FONT);
        f.setForeground(TEXT);
        f.setBackground(Color.WHITE);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(6, 10, 6, 10)
        ));
        f.setPreferredSize(new Dimension(90, 34));
    }

    private void append(String message) {
        SwingUtilities.invokeLater(() ->
                activity.append(LocalTime.now().withNano(0) + "  " + message + "\n"));
    }

    /**
     * Làm mới bảng nhân viên.
     * Giả định: server.connectedUsers() trả về List<String> dạng "user|role|status"
     * hoặc chỉ "user". Nếu chỉ có tên, chức năng & trạng thái để mặc định.
     * Điều chỉnh parse theo định dạng thực tế của RestaurantServer.
     */
    private void refreshEmployees() {
        employeeModel.setRowCount(0);
        if (server == null) return;

        for (String entry : server.connectedUsers()) {
            String user, role, status;
            if (entry.contains("|")) {
                String[] parts = entry.split("\\|", -1);
                user   = parts.length > 0 ? parts[0] : "";
                role   = parts.length > 1 ? parts[1] : "—";
                status = parts.length > 2 ? parts[2] : "Online";
            } else {
                user = entry;
                role = "—";
                status = "Online";
            }
            employeeModel.addRow(new Object[]{user, role, status});
        }
    }

    /** Renderer hiển thị trạng thái dưới dạng badge màu. */
    private static class StatusBadgeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            String s = value == null ? "" : value.toString().toLowerCase();

            if (s.contains("online") || s.contains("active") || s.contains("rảnh")) {
                lbl.setForeground(SUCCESS);
            } else if (s.contains("offline") || s.contains("busy") || s.contains("bận")) {
                lbl.setForeground(DANGER);
            } else {
                lbl.setForeground(MUTED);
            }

            lbl.setFont(FONT_BOLD);
            lbl.setBorder(new EmptyBorder(0, 8, 0, 8));
            if (!isSelected) lbl.setBackground(Color.WHITE);
            return lbl;
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new ServerDashboard().setVisible(true));
    }
}