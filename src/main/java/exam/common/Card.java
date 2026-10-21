package exam.common;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/** Thẻ trắng bo góc có viền mảnh; nội dung cách mép 3 ô lưới. */
public class Card extends JPanel {
    public Card() {
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Palette.GAP * 3, Palette.GAP * 3, Palette.GAP * 3, Palette.GAP * 3));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Palette.CARD);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Palette.RADIUS, Palette.RADIUS);
        g2.setColor(Palette.BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Palette.RADIUS, Palette.RADIUS);
        g2.dispose();
        super.paintComponent(g);
    }
}
