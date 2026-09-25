package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/** Hình minh hoạ món ăn vẽ bằng Java2D. */
public class FoodArt extends JComponent {

    private final String kind;

    public FoodArt(String kind) {
        this.kind = kind;
        setPreferredSize(new Dimension(180, 116));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D x = (Graphics2D) g.create();
        x.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();

        x.setPaint(new GradientPaint(
                0, 0, new Color(255, 246, 232),
                w, h, new Color(252, 228, 210)));
        x.fillRoundRect(0, 0, w, h, 18, 18);

        double sc = Math.min(w / 180.0, h / 116.0);
        x.translate((w - 180 * sc) / 2, (h - 116 * sc) / 2);
        x.scale(sc, sc);

        // ─── Đĩa ───
        x.setColor(new Color(211, 189, 164, 70));
        x.fillOval(30, 14, 122, 96);
        x.setColor(Color.WHITE);
        x.fillOval(35, 9, 110, 94);
        x.setColor(new Color(238, 235, 225));
        x.setStroke(new BasicStroke(2));
        x.drawOval(42, 16, 96, 80);

        switch (kind) {
            case "STEAK", "GRILL" -> {
                x.setColor(new Color(105, 52, 34));
                x.fillRoundRect(65, 36, 62, 38, 22, 22);
                x.setColor(new Color(158, 80, 49));
                x.fillRoundRect(69, 40, 54, 30, 18, 18);
                x.setStroke(new BasicStroke(3));
                x.setColor(new Color(90, 44, 31));
                for (int i = 0; i < 3; i++)
                    x.drawLine(78 + i * 14, 43, 72 + i * 14, 63);
                greens(x);
            }
            case "PASTA" -> {
                x.setColor(new Color(239, 180, 69));
                for (int i = 0; i < 7; i++)
                    x.draw(new Arc2D.Double(55 + i * 7, 34 + i % 2 * 5, 52, 33, 0, 270, Arc2D.OPEN));
                x.setColor(new Color(195, 73, 48));
                for (int i = 0; i < 6; i++)
                    x.fillOval(65 + i * 8, 43 + i % 3 * 8, 9, 8);
                greens(x);
            }
            case "PIZZA" -> {
                x.setColor(new Color(222, 159, 78));
                x.fillOval(55, 25, 73, 68);
                x.setColor(new Color(250, 211, 108));
                x.fillOval(61, 31, 61, 56);
                x.setColor(new Color(188, 72, 58));
                for (int i = 0; i < 5; i++)
                    x.fillOval(65 + (i * 13) % 48, 35 + (i * 17) % 42, 12, 10);
                greens(x);
            }
            case "SALAD" -> {
                greens(x);
                x.setColor(new Color(225, 65, 55));
                for (int i = 0; i < 4; i++)
                    x.fillOval(72 + i * 11, 45 + (i % 2) * 15, 10, 9);
            }
            case "RICE" -> {
                x.setColor(new Color(246, 239, 209));
                x.fillOval(56, 38, 56, 40);
                x.setColor(new Color(171, 103, 52));
                x.fillRoundRect(89, 37, 35, 28, 13, 13);
                greens(x);
            }
            case "SOUP" -> {
                x.setColor(new Color(222, 135, 56));
                x.fillOval(59, 32, 65, 50);
                x.setColor(new Color(247, 178, 78));
                x.fillOval(65, 37, 53, 40);
                x.setColor(Color.WHITE);
                x.setStroke(new BasicStroke(3));
                x.drawArc(78, 46, 27, 20, 0, 250);
                greens(x);
            }
            case "FISH" -> {
                // cá hồi áp chảo
                x.setColor(new Color(240, 130, 100));
                x.fillRoundRect(58, 42, 64, 32, 16, 16);
                x.setColor(new Color(255, 168, 140));
                x.fillRoundRect(62, 46, 56, 24, 12, 12);
                x.setColor(new Color(200, 80, 60));
                x.setStroke(new BasicStroke(2));
                for (int i = 0; i < 4; i++)
                    x.drawArc(66 + i * 13, 46, 14, 24, 0, 180);
                greens(x);
            }
            case "BURGER" -> {
                x.setColor(new Color(200, 140, 80));
                x.fillRoundRect(60, 34, 60, 12, 8, 8);  // bun trên
                x.setColor(new Color(90, 160, 90));
                x.fillRect(58, 46, 64, 7);              // rau
                x.setColor(new Color(150, 70, 45));
                x.fillRoundRect(60, 53, 60, 14, 6, 6);  // patty
                x.setColor(new Color(255, 210, 90));
                x.fillRect(58, 67, 64, 6);              // cheese
                x.setColor(new Color(200, 140, 80));
                x.fillRoundRect(60, 73, 60, 12, 8, 8);  // bun dưới
            }
            case "NOODLE" -> {
                x.setColor(new Color(245, 220, 160));
                for (int i = 0; i < 6; i++)
                    x.drawArc(60 + i * 6, 38 + i % 2 * 6, 50, 34, 0, 270);
                x.setColor(new Color(220, 90, 60));
                x.fillOval(78, 50, 14, 12);
                greens(x);
            }
            case "TEA", "JUICE", "SMOOTHIE" -> {
                Color liquid = switch (kind) {
                    case "TEA"      -> new Color(225, 141, 57);
                    case "JUICE"    -> new Color(240, 170, 50);
                    default         -> new Color(250, 180, 130);
                };
                x.setColor(liquid);
                x.fillRoundRect(76, 30, 35, 54, 5, 5);
                x.setColor(new Color(255, 255, 255, 140));
                x.fillRect(80, 34, 5, 44);
                x.setColor(new Color(70, 130, 75));
                x.fillOval(101, 26, 14, 7);
                x.setStroke(new BasicStroke(3));
                x.drawLine(96, 34, 111, 16);
            }
            case "COFFEE" -> {
                x.setColor(new Color(123, 72, 44));
                x.fillRoundRect(76, 30, 35, 54, 5, 5);
                x.setColor(new Color(255, 255, 255, 140));
                x.fillRect(80, 34, 5, 44);
                x.setColor(new Color(90, 50, 30));
                x.fillOval(80, 30, 27, 6);
            }
            case "CAKE" -> {
                x.setColor(new Color(123, 77, 57));
                x.fillRoundRect(63, 42, 63, 36, 4, 4);
                x.setColor(new Color(242, 224, 195));
                x.fillRect(63, 42, 63, 10);
                x.setColor(new Color(101, 59, 46));
                x.fillRect(63, 52, 63, 7);
                x.setColor(new Color(238, 224, 205));
                x.fillRect(63, 60, 63, 9);
            }
            default -> greens(x);
        }

        x.dispose();
    }

    private void greens(Graphics2D x) {
        x.setColor(new Color(76, 150, 84));
        for (int i = 0; i < 5; i++)
            x.fill(new Ellipse2D.Double(56 + i * 14, 57 + (i % 2) * 8, 16, 8));
    }
}