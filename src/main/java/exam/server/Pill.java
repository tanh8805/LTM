// Hộp bo tròn có nền và viền: dùng cho đồng hồ, địa chỉ và nhóm tab của cửa sổ giám thị (Người 2).
package exam.server;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/** Hộp bo tròn có nền và viền: dùng cho đồng hồ, địa chỉ và nhóm tab. */
class Pill extends JPanel {
    private final Color fill, line;

    Pill(Color fill, Color line, int padX, int padY) {
        super(new FlowLayout(FlowLayout.CENTER, 8, 0));
        this.fill = fill;
        this.line = line;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(padY, padX, padY, padX));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        if (line != null) {
            g2.setColor(line);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        }
        g2.dispose();
    }
}
