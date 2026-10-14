// Màu, font, biểu tượng và vài hàm dựng thành phần dùng chung cho giao diện sinh viên; không có logic nào khác (Người 1).
package exam.client;

import com.formdev.flatlaf.FlatLaf;
import exam.common.Icons;
import exam.common.Palette;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

final class Theme {
    static final Color PRIMARY = Palette.PRIMARY, PRIMARY_SOFT = Palette.PRIMARY_SOFT, WARNING = Palette.WARNING,
        WARNING_SOFT = Palette.WARNING_SOFT, ERROR = Palette.ERROR, SUCCESS = Palette.SUCCESS,
        BACKGROUND = Palette.BACKGROUND, CARD = Palette.CARD, TEXT = Palette.TEXT, MUTED = Palette.MUTED, BORDER = Palette.BORDER;
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

    /** Biểu tượng tự vẽ nằm ở exam.common.Icons để giám thị dùng chung. */
    static Icon warningIcon(int size) { return Icons.warning(size); }

    static Icon checkIcon(int size) { return Icons.check(size); }
}
