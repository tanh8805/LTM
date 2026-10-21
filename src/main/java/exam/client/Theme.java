// Màu, font, biểu tượng và vài hàm dựng thành phần dùng chung cho giao diện sinh viên; không có logic nào khác (Người 1).
package exam.client;

import exam.common.Icons;
import exam.common.Look;
import exam.common.Palette;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.UIManager;

final class Theme {
    static final Color PRIMARY = Palette.PRIMARY, PRIMARY_SOFT = Palette.PRIMARY_SOFT, WARNING = Palette.WARNING,
        WARNING_SOFT = Palette.WARNING_SOFT, ERROR = Palette.ERROR, SUCCESS = Palette.SUCCESS,
        BACKGROUND = Palette.BACKGROUND, CARD = Palette.CARD, TEXT = Palette.TEXT, MUTED = Palette.MUTED, BORDER = Palette.BORDER,
        SEPARATOR = Palette.SEPARATOR, NEUTRAL = Palette.NEUTRAL_SOFT;
    static final float BASE_SIZE = 15f;

    private Theme() {}

    /** Gọi một lần ở đầu main, trước khi tạo cửa sổ. */
    static void install() { Look.install(BASE_SIZE); }

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

    /** Biểu tượng tự vẽ nằm ở exam.common.Icons để giám thị dùng chung. */
    static Icon warningIcon(int size) { return Icons.warning(size); }

    static Icon checkIcon(int size) { return Icons.check(size); }
}
