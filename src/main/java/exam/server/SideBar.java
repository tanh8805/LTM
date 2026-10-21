package exam.server;

import exam.common.Palette;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Thanh bên trái của cửa sổ giám thị: mục đang xem tô xanh, các mục còn lại mở hộp thoại tương ứng. */
class SideBar extends JPanel {
    private final Map<String, JButton> items = new HashMap<>();

    SideBar() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Palette.CARD);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Palette.SEPARATOR),
            BorderFactory.createEmptyBorder(Palette.GAP * 2, Palette.GAP * 2, Palette.GAP * 2, Palette.GAP * 2)));
        setPreferredSize(new Dimension(Palette.GAP * 24, 0));
    }

    /** selected = mục màn hình hiện tại (không làm gì khi bấm). */
    void addItem(String key, String text, boolean selected, Runnable action) {
        JButton b = new JButton(text);
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setFocusable(false);
        b.setFont(b.getFont().deriveFont(selected ? java.awt.Font.BOLD : java.awt.Font.PLAIN, 14.5f));
        b.setMargin(new java.awt.Insets(Palette.GAP + 2, Palette.GAP * 2, Palette.GAP + 2, Palette.GAP * 2));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, Palette.GAP * 6));
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setForeground(selected ? Palette.PRIMARY_TEXT : Palette.TEXT);
        if (selected) {
            b.setContentAreaFilled(true);
            b.setBackground(Palette.PRIMARY_SOFT);
        }
        b.addActionListener(e -> action.run());
        items.put(key, b);
        add(b);
        add(Box.createVerticalStrut(4));
    }

    void setText(String key, String text) { items.get(key).setText(text); }

    void setEnabled(String key, boolean enabled) { items.get(key).setEnabled(enabled); }
}
