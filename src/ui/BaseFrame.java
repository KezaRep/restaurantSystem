package ui;

import client.NetworkClient;
import model.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;

public abstract class BaseFrame extends JFrame {

    // ─── State ───
    protected final Map<Long, Protocol.Order> orders = new LinkedHashMap<>();
    protected NetworkClient net;

    // ─── Component dùng chung ───
    protected JLabel connection = Theme.label("Chưa kết nối", 12, true, Theme.MUTED);
    protected JPanel content    = Theme.panel(new BorderLayout(0, 18));
    protected JTextField host   = Theme.field("localhost", 12);
    protected JTextField port   = Theme.field("8080", 5);

    // ─── Điều hướng ───
    private final JPanel breadcrumb = Theme.panel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final Deque<Runnable> history = new ArrayDeque<>();
    private JButton backBtn;

    // ═══════════════════════════════════════════
    //  KHỞI TẠO
    // ═══════════════════════════════════════════
    protected BaseFrame(String title, String role) {
        super(title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 840);
        setMinimumSize(new Dimension(1000, 680));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        setContentPane(root);

        root.add(buildSidebar(), BorderLayout.WEST);

        JPanel main = Theme.panel(new BorderLayout());
        main.setBorder(new EmptyBorder(22, 28, 24, 28));
        root.add(main, BorderLayout.CENTER);

        main.add(buildTopBar(title), BorderLayout.NORTH);

        content.setBorder(new EmptyBorder(16, 0, 0, 0));
        main.add(content, BorderLayout.CENTER);

        initNetwork(role);
    }

    // ═══════════════════════════════════════════
    //  SIDEBAR
    // ═══════════════════════════════════════════
    private JPanel buildSidebar() {
        JPanel side = new JPanel();
        side.setBackground(Theme.NAVY);
        side.setPreferredSize(new Dimension(228, 0));
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(new EmptyBorder(32, 22, 24, 22));

        JLabel brand = Theme.label("RESTAURANT SYSTEM", 24, true, Color.WHITE);
        brand.setAlignmentX(LEFT_ALIGNMENT);
        side.add(brand);

        side.add(Box.createVerticalStrut(8));


        String[] items = sideItems();
        for (int i = 0; i < items.length; i++) {
            boolean active = i == 0;
            Color fg = active ? new Color(255, 179, 134) : new Color(183, 192, 207);

            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(active);
            row.setBackground(active ? Theme.NAVY_SOFT : Theme.NAVY);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
            row.setBorder(new EmptyBorder(0, 12, 0, 8));
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel l = Theme.label(items[i], 14, true, fg);
            row.add(l, BorderLayout.CENTER);

            if (active) {
                JPanel bar = new JPanel();
                bar.setBackground(Theme.ORANGE);
                bar.setPreferredSize(new Dimension(3, 0));
                row.add(bar, BorderLayout.WEST);
            }

            int idx = i;
            row.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    navigateTo(items[idx]);
                }
                @Override public void mouseEntered(MouseEvent e) {
                    if (!active) row.setBackground(Theme.NAVY_SOFT);
                }
                @Override public void mouseExited(MouseEvent e) {
                    if (!active) row.setBackground(Theme.NAVY);
                }
            });

            side.add(row);
            side.add(Box.createVerticalStrut(6));
        }

        side.add(Box.createVerticalGlue());

        JLabel foot = Theme.label("TCP/IP  •  JAVA 21", 11, false, new Color(159, 171, 191));
        foot.setAlignmentX(LEFT_ALIGNMENT);
        side.add(foot);

        return side;
    }

    /** Lớp con override để xử lý chuyển trang */
    protected void navigateTo(String item) {
        // mặc định: không làm gì; lớp con override nếu cần
    }

    // ═══════════════════════════════════════════
    //  TOP BAR
    // ═══════════════════════════════════════════
    private JPanel buildTopBar(String title) {
        JPanel wrapper = Theme.panel(new BorderLayout(0, 8));

        JPanel top = Theme.panel(new BorderLayout());

        // ─── Trái: nút back + breadcrumb + tiêu đề ───
        JPanel left = Theme.panel(new BorderLayout(0, 4));

        JPanel crumbRow = Theme.panel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        backBtn = Theme.ghost("← Quay lại");
        backBtn.setVisible(false);
        backBtn.setBorder(new EmptyBorder(4, 10, 4, 10));
        backBtn.addActionListener(e -> goBack());
        crumbRow.add(backBtn);

        breadcrumb.setOpaque(false);
        crumbRow.add(breadcrumb);
        left.add(crumbRow, BorderLayout.NORTH);

        JPanel titles = Theme.panel(new GridLayout(2, 1, 0, 4));
        titles.add(Theme.label(title, 26, true, Theme.NAVY));
        titles.add(Theme.label(subtitle(), 13, false, Theme.MUTED));
        left.add(titles, BorderLayout.CENTER);

        top.add(left, BorderLayout.WEST);

        // ─── Phải: kết nối ───
        JPanel conn = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        conn.add(connection);
        conn.add(host);
        conn.add(port);

        JButton connect = Theme.button("Kết nối", Theme.ORANGE);
        connect.setName("btnConnect");
        conn.add(connect);
        top.add(conn, BorderLayout.EAST);

        wrapper.add(top, BorderLayout.CENTER);
        return wrapper;
    }

    // ═══════════════════════════════════════════
    //  ĐIỀU HƯỚNG
    // ═══════════════════════════════════════════
    /** Ghi nhớ trạng thái hiện tại rồi chuyển trang */
    protected void pushHistory(Runnable restore) {
        history.push(restore);
        updateNavUi();
    }

    private void goBack() {
        if (history.isEmpty()) return;
        Runnable r = history.pop();
        r.run();
        updateNavUi();
    }

    private void updateNavUi() {
        if (backBtn != null) backBtn.setVisible(!history.isEmpty());
        breadcrumb.revalidate();
        breadcrumb.repaint();
    }

    /** Cập nhật breadcrumb: ví dụ ["Trang chủ", "Menu"] */
    protected void setBreadcrumb(String... parts) {
        breadcrumb.removeAll();
        for (int i = 0; i < parts.length; i++) {
            Color c = (i == parts.length - 1) ? Theme.NAVY : Theme.MUTED;
            boolean bold = (i == parts.length - 1);
            breadcrumb.add(Theme.label(parts[i], 12, bold, c));
            if (i < parts.length - 1)
                breadcrumb.add(Theme.label("›", 12, false, Theme.MUTED));
        }
        breadcrumb.revalidate();
        breadcrumb.repaint();
    }

    // ═══════════════════════════════════════════
    //  NETWORK
    // ═══════════════════════════════════════════
    private void initNetwork(String role) {
        net = new NetworkClient(
                role,
                m -> SwingUtilities.invokeLater(() -> receive(m)),
                s -> SwingUtilities.invokeLater(() -> {
                    connection.setText(s);
                    connection.setForeground(
                            s.startsWith("Đã") ? Theme.GREEN : Theme.ORANGE);
                }));

        JButton connect = findButton(getContentPane(), "Kết nối");
        if (connect != null) {
            connect.addActionListener(e -> {
                try {
                    net.close();
                    net.connect(host.getText().trim(),
                            Integer.parseInt(port.getText().trim()));
                } catch (NumberFormatException ex) {
                    notice("Cổng không hợp lệ");
                }
            });
            SwingUtilities.invokeLater(connect::doClick);
        }
    }

    private JButton findButton(Container c, String text) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JButton b && text.equals(b.getText())) return b;
            if (comp instanceof Container inner) {
                JButton found = findButton(inner, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════
    //  NHẬN MESSAGE
    // ═══════════════════════════════════════════
    private void receive(Message m) {
        if (m.getType() == Message.Type.SNAPSHOT) {
            orders.clear();
            if (!m.getContent().isBlank()) {
                for (String s : m.getContent().split("\n")) {
                    try {
                        Protocol.Order o = Protocol.parse(s);
                        orders.put(o.id(), o);
                    } catch (RuntimeException ignored) {}
                }
            }
            render();
        } else if (m.getType() == Message.Type.ERROR) {
            notice(m.getContent());
        } else {
            onMessage(m);
        }
    }

    // ═══════════════════════════════════════════
    //  TIỆN ÍCH
    // ═══════════════════════════════════════════
    protected void notice(String msg) {
        JOptionPane.showMessageDialog(this, msg, "SAVORY",
                JOptionPane.INFORMATION_MESSAGE);
    }

    protected void send(Message.Type type, int table, String text) {
        net.send(new Message(type, role(), table, text));
    }

    protected JScrollPane scroll(Component c) {
        JScrollPane s = new JScrollPane(c);
        Theme.scroll(s);
        return s;
    }

    // ═══════════════════════════════════════════
    //  ABSTRACT — lớp con override
    // ═══════════════════════════════════════════
    protected abstract String[] sideItems();
    protected abstract String subtitle();
    protected abstract void render();
    protected abstract String role();
    protected void onMessage(Message m) {}
}