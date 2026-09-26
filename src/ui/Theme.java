package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Bảng màu + factory component dùng chung. */
public final class Theme {

    // ─── Màu chủ đạo ───
    public static final Color BG        = new Color(247, 245, 240);
    public static final Color CARD      = Color.WHITE;
    public static final Color NAVY      = new Color(28, 41, 61);
    public static final Color NAVY_SOFT = new Color(43, 58, 82);
    public static final Color ORANGE    = new Color(232, 122, 65);
    public static final Color ORANGE_DK = new Color(198, 92, 42);
    public static final Color GREEN     = new Color(76, 175, 80);
    public static final Color RED       = new Color(214, 69, 69);
    public static final Color MUTED     = new Color(140, 148, 162);
    public static final Color BORDER    = new Color(228, 224, 216);
    public static final Color CREAM     = new Color(255, 244, 228);

    private Theme() {}

    public static JPanel panel(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setOpaque(false);
        return p;
    }

    public static JLabel label(String text, int size, boolean bold, Color fg) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(fg);
        return l;
    }

    public static JTextField field(String text, int cols) {
        JTextField f = new JTextField(text, cols);
        f.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)));
        f.setBackground(Color.WHITE);
        return f;
    }

    public static JButton button(String text, Color bg) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D x = (Graphics2D) g.create();
                x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                boolean pressed = getModel().isPressed();
                boolean hover = getModel().isRollover();
                
                if (pressed) {
                    x.setColor(bg.darker());
                } else if (hover) {
                    // blend with white for a brighter hover effect
                    x.setColor(new Color(
                        Math.min(255, bg.getRed() + 20),
                        Math.min(255, bg.getGreen() + 20),
                        Math.min(255, bg.getBlue() + 20)
                    ));
                } else {
                    x.setColor(bg);
                }
                
                x.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                x.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new EmptyBorder(9, 18, 9, 18));
        return b;
    }

    public static JButton ghost(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setForeground(Theme.NAVY);
        b.setBackground(new Color(240, 236, 228));
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(9, 18, 9, 18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setOpaque(true);
        return b;
    }

    public static void scroll(JScrollPane s) {
        s.setBorder(BorderFactory.createEmptyBorder());
        s.setOpaque(false);
        s.getViewport().setOpaque(false);
        s.getVerticalScrollBar().setUnitIncrement(18);
        s.getHorizontalScrollBar().setUnitIncrement(18);
    }

    /** Card bo góc có bóng nhẹ */
    public static JPanel card() {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D x = (Graphics2D) g.create();
                x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                // shadow mượt hơn
                x.setColor(new Color(0, 0, 0, 10));
                x.fillRoundRect(4, 6, getWidth() - 8, getHeight() - 8, 18, 18);
                x.setColor(new Color(0, 0, 0, 6));
                x.fillRoundRect(2, 4, getWidth() - 4, getHeight() - 4, 18, 18);
                // body
                x.setColor(CARD);
                x.fillRoundRect(0, 0, getWidth() - 6, getHeight() - 8, 16, 16);
                x.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(14, 16, 16, 20));
        return p;
    }
    
 // ─── trong Theme.java, thêm method này ───
    private static final java.text.NumberFormat VND =
            java.text.NumberFormat.getInstance(new java.util.Locale("vi", "VN"));

    public static String money(long amount) {
        return VND.format(amount) + " đ";
    }
    
 // ─── trong Theme.java, thêm method này ───
    public static JPanel card(LayoutManager lm) {
        JPanel p = card();          // gọi bản không tham số
        p.setLayout(lm);
        return p;
    }
}