package ui;

import model.Menu;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Panel hiển thị menu: tìm kiếm, tab nhóm, lưới món. */
public class MenuPanel extends JPanel {

    private final JTextField searchField = Theme.field("", 20);
    private final JPanel grid = Theme.panel(new GridLayout(0, 3, 16, 16));
    private final JPanel tabBar = Theme.panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final Consumer<Menu.Dish> onPick;

    private String currentCategory = "Tất cả";

    public MenuPanel(Consumer<Menu.Dish> onPick) {
        this.onPick = onPick;
        setLayout(new BorderLayout(0, 14));
        setOpaque(false);

        add(buildHeader(), BorderLayout.NORTH);

        JPanel listWrap = new JPanel(new BorderLayout());
        listWrap.setOpaque(false);
        listWrap.add(grid, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(listWrap);
        Theme.scroll(scroll);
        scroll.getViewport().setOpaque(false);
        add(scroll, BorderLayout.CENTER);

        render();
    }

    // ─── Header: search + tabs ───
    private JPanel buildHeader() {
        JPanel header = Theme.panel(new BorderLayout(0, 12));

        // Thanh tìm kiếm
        JPanel searchBar = Theme.panel(new BorderLayout(8, 0));
        searchBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                new EmptyBorder(6, 12, 6, 6)));
        searchBar.setBackground(Color.WHITE);
        searchBar.setOpaque(true);

        JLabel icon = Theme.label("🔍", 14, false, Theme.MUTED);
        searchBar.add(icon, BorderLayout.WEST);

        searchField.setBorder(null);
        searchField.setOpaque(false);
        searchField.putClientProperty("JTextField.placeholderText", "Tìm món ăn...");
        searchField.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) { render(); }
        });
        searchBar.add(searchField, BorderLayout.CENTER);

        JButton clear = Theme.ghost("Xoá");
        clear.setBorder(new EmptyBorder(6, 12, 6, 12));
        clear.addActionListener(e -> { searchField.setText(""); render(); });
        searchBar.add(clear, BorderLayout.EAST);

        header.add(searchBar, BorderLayout.NORTH);

        // Tabs nhóm
        tabBar.setOpaque(false);
        header.add(tabBar, BorderLayout.SOUTH);

        rebuildTabs();
        return header;
    }

    private void rebuildTabs() {
        tabBar.removeAll();
        List<String> cats = new ArrayList<>();
        cats.add("Tất cả");
        cats.addAll(Menu.CATEGORY_ORDER);

        for (String c : cats) {
            tabBar.add(makeTab(c));
        }
        tabBar.revalidate();
        tabBar.repaint();
    }

    private JButton makeTab(String category) {
        boolean active = category.equals(currentCategory);
        JButton b = new JButton(category) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D x = (Graphics2D) g.create();
                x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                x.setColor(active ? Theme.NAVY : new Color(240, 236, 228));
                x.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                x.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setForeground(active ? Color.WHITE : Theme.NAVY);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new EmptyBorder(8, 16, 8, 16));
        b.addActionListener(e -> {
            currentCategory = category;
            rebuildTabs();
            render();
        });
        return b;
    }

    // ─── Lưới món ───
    private void render() {
        grid.removeAll();

        String keyword = searchField.getText().trim();
        List<Menu.Dish> source = keyword.isEmpty()
                ? Menu.all()
                : Menu.search(keyword);

        Map<String, List<Menu.Dish>> grouped = new LinkedHashMap<>();
        for (String c : Menu.CATEGORY_ORDER) {
            List<Menu.Dish> list = new ArrayList<>();
            for (Menu.Dish d : source) {
                if (d.category().equals(c)) list.add(d);
            }
            if (!list.isEmpty()) grouped.put(c, list);
        }

        boolean showAll = currentCategory.equals("Tất cả");

        for (Map.Entry<String, List<Menu.Dish>> e : grouped.entrySet()) {
            if (!showAll && !e.getKey().equals(currentCategory)) continue;

            // Tiêu đề nhóm
            if (showAll) {
                JLabel section = Theme.label(e.getKey().toUpperCase(), 13, true, Theme.ORANGE_DK);
                section.setBorder(new EmptyBorder(14, 4, 2, 0));
                grid.add(section);

                // filler cho hàng tiêu đề
                grid.add(new JLabel());
                grid.add(new JLabel());
            }

            for (Menu.Dish d : e.getValue()) {
                grid.add(new DishCard(d, onPick));
            }
        }

        if (grid.getComponentCount() == 0) {
            JLabel empty = Theme.label("Không tìm thấy món nào 😢", 14, false, Theme.MUTED);
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            empty.setBorder(new EmptyBorder(40, 0, 0, 0));
            grid.add(empty);
        }

        grid.revalidate();
        grid.repaint();
    }

    /** Cho phép mở rộng số cột linh hoạt — gọi từ ngoài nếu muốn */
    public void setColumns(int cols) {
        ((GridLayout) grid.getLayout()).setColumns(cols);
        grid.revalidate();
    }
}