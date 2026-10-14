// Tab lọc dạng nút gạt của cửa sổ giám thị (Người 2).
package exam.server;

import exam.common.Palette;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.JToggleButton;

/** Tab dạng nút gạt: tab đang chọn nền trắng, còn lại trong suốt trên nền xám của nhóm. */
class TabButton extends JToggleButton {
    TabButton() {
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setFocusable(false);
        setMargin(new Insets(5, 14, 5, 14));
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (isSelected()) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.setColor(Palette.BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
