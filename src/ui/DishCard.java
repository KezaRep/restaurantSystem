package ui;

import model.Menu;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Consumer;

/** Thẻ hiển thị một món ăn, có hover và click. */
public class DishCard extends JPanel {

    private static final NumberFormat VND = NumberFormat.getInstance(new Locale("vi", "VN"));
    private boolean hover = false;

    public DishCard(Menu.Dish dish, Consumer<Menu.Dish> onClick) {
        setLayout(new BorderLayout(0, 8));
        setOpaque(false);
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // ─── Hình minh hoạ ───
        FoodArt art = new FoodArt(dish.icon());
        art.setPreferredSize(new Dimension(0, 116));
        add(art, BorderLayout.NORTH);

        // ─── Thông tin ───
        JPanel info = Theme.panel(new BorderLayout(0, 4));

        JLabel name = Theme.label(dish.name(), 14, true, Theme.NAVY);
        info.add(name, BorderLayout.NORTH);

        JLabel desc = Theme.label(
                "<html><div style='width:150px'>" + dish.description() + "</div></html>",
                11, false, Theme.MUTED);
        info.add(desc, BorderLayout.CENTER);

        JPanel bottom = Theme.panel(new BorderLayout());
        JLabel price = Theme.label(VND.format(dish.price()) + " đ", 13, true, Theme.ORANGE_DK);
        bottom.add(price, BorderLayout.WEST);

        JLabel cat = Theme.label(dish.category(), 10, false, Theme.MUTED);
        bottom.add(cat, BorderLayout.EAST);

        info.add(bottom, BorderLayout.SOUTH);
        add(info, BorderLayout.CENTER);

        // ─── Hover & click ───
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover = true;  repaint(); }
            @Override public void mouseExited (MouseEvent e) { hover = false; repaint(); }
            @Override public void mouseClicked(MouseEvent e) {
                if (onClick != null) onClick.accept(dish);
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D x = (Graphics2D) g.create();
        x.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int lift = hover ? 0 : 4;
        // shadow
        x.setColor(new Color(0, 0, 0, hover ? 26 : 12));
        x.fillRoundRect(3, 5 + lift, getWidth() - 8, getHeight() - 8 - lift, 18, 18);
        // body
        x.setColor(hover ? new Color(255, 252, 246) : Color.WHITE);
        x.fillRoundRect(0, 0 + lift, getWidth() - 8, getHeight() - 8 - lift, 18, 18);
        // border
        x.setColor(hover ? Theme.ORANGE : Theme.BORDER);
        x.setStroke(new BasicStroke(hover ? 2f : 1f));
        x.drawRoundRect(0, 0 + lift, getWidth() - 9, getHeight() - 9 - lift, 18, 18);
        x.dispose();
        super.paintComponent(g);
    }
}