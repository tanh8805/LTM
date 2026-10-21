// Nút số câu trong lưới bên trái màn làm bài (Người 1).
package exam.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;

/** Nút số câu: đã làm tô màu chính, câu đang xem có viền đậm, chưa làm để trắng. */
class NumberButton extends JButton {
    private boolean done, current;

    NumberButton(int n) {
        super(String.valueOf(n));
        setFocusable(false);
        setFont(Theme.font(13, true));
        setPreferredSize(new Dimension(40, 36));
        setMargin(new java.awt.Insets(0, 0, 0, 0));      // margin mặc định của FlatLaf làm số bị cắt thành "..."
        setContentAreaFilled(false);
        setBorderPainted(false);
    }

    void setDone(boolean d) { done = d; repaint(); }

    void setCurrent(boolean c) { current = c; repaint(); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(done ? Theme.PRIMARY : Color.WHITE);
        g2.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 9, 9);
        g2.setStroke(new BasicStroke(current ? 2.5f : 1f));
        g2.setColor(current ? Theme.TEXT : done ? Theme.PRIMARY : Theme.BORDER);
        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 9, 9);
        g2.dispose();
        setForeground(done ? Color.WHITE : Theme.TEXT);
        super.paintComponent(g);
    }
}
