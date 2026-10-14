// Màu, font, biểu tượng và vài hàm dựng thành phần dùng chung cho giao diện sinh viên; không có logic nào khác (Người 1).
package exam.client;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

final class Theme {
    static final Color PRIMARY = new Color(0x1F6FEB), PRIMARY_SOFT = new Color(0xE4EEFF), WARNING = new Color(0xE67700),
        WARNING_SOFT = new Color(0xFFF1DB), ERROR = new Color(0xD92D20), SUCCESS = new Color(0x1E9E50),
        BACKGROUND = new Color(0xF4F6FA), CARD = Color.WHITE, TEXT = new Color(0x1F2937), MUTED = new Color(0x6B7280),
        BORDER = new Color(0xD3D9E3);
    static final float BASE_SIZE = 15f;

    private Theme() {}

    /** Gọi một lần ở đầu main, trước khi tạo cửa sổ. Giữ font mặc định của hệ điều hành (Segoe UI trên Windows hỗ trợ tiếng Việt). */
    static void install() {
        FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor", "#1F6FEB"));
        FlatLightLaf.setup();
        UIManager.put("defaultFont", UIManager.getFont("defaultFont").deriveFont(BASE_SIZE));
        UIManager.put("Button.arc", 14);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("ProgressBar.arc", 999);
    }

    static Font font(float size, boolean bold) {
        Font f = UIManager.getFont("defaultFont");
        return (f == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 15) : f).deriveFont(bold ? Font.BOLD : Font.PLAIN, size);
    }

    static JLabel label(String text, float size, boolean bold, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(size, bold));
        l.setForeground(color);
        return l;
    }

    static JButton primaryButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(PRIMARY);
        b.setForeground(Color.WHITE);
        b.setFont(font(BASE_SIZE, true));
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.setMargin(new Insets(10, 24, 10, 24));      // dùng margin thay vì border để giữ góc bo của FlatLaf
        return b;
    }

    /** Màu đồng hồ: bình thường xanh lá, dưới 5 phút cam, dưới 1 phút đỏ. */
    static Color timerColor(int secondsLeft) {
        return secondsLeft < 60 ? ERROR : secondsLeft < 300 ? WARNING : SUCCESS;
    }

    /** Khung trắng bo góc có viền mảnh. */
    static class Card extends JPanel {
        Card() {
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(CARD);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Biểu tượng tự vẽ để không phụ thuộc font có ký tự đặc biệt hay không. */
    static Icon warningIcon(int size) { return new Glyph(size, WARNING, true); }

    static Icon checkIcon(int size) { return new Glyph(size, SUCCESS, false); }

    private record Glyph(int size, Color color, boolean warning) implements Icon {
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.setColor(color);
            float s = size, w = Math.max(2f, s / 10);
            if (warning) {                                           // tam giác cảnh báo có dấu chấm than
                Path2D tri = new Path2D.Float();
                tri.moveTo(s / 2, s * 0.06);
                tri.lineTo(s * 0.97, s * 0.92);
                tri.lineTo(s * 0.03, s * 0.92);
                tri.closePath();
                g2.fill(tri);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(size / 2, (int) (s * 0.35), size / 2, (int) (s * 0.62));
                g2.fillOval((int) (s / 2 - w / 2), (int) (s * 0.72), (int) w, (int) w);
            } else {                                                 // hình tròn xanh có dấu tích
                g2.fillOval(0, 0, size, size);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D tick = new Path2D.Float();
                tick.moveTo(s * 0.27, s * 0.52);
                tick.lineTo(s * 0.44, s * 0.68);
                tick.lineTo(s * 0.74, s * 0.33);
                g2.draw(tick);
            }
            g2.dispose();
        }
    }
}
