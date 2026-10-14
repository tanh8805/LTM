// Một hàng lựa chọn A/B/C/D trong màn làm bài: bấm vào đâu trên hàng cũng chọn được (Người 1).
package exam.client;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

/** Một lựa chọn: bấm vào đâu trên hàng cũng chọn được, hàng đang chọn được tô màu. */
class OptionRow extends JPanel {
    private boolean selected, hover;

    OptionRow(char letter, String text, boolean selected, IntConsumer onPick, int index) {
        super(new BorderLayout(16, 0));
        this.selected = selected;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        setAlignmentX(LEFT_ALIGNMENT);
        JLabel badge = Theme.label(String.valueOf(letter), 18, true, Theme.PRIMARY);
        badge.setHorizontalAlignment(SwingConstants.CENTER);
        badge.setPreferredSize(new Dimension(30, 30));
        JTextArea body = new JTextArea(text);
        body.setEditable(false);
        body.setFocusable(false);
        body.setOpaque(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setFont(Theme.font(17, false));
        body.setForeground(Theme.TEXT);
        add(badge, BorderLayout.WEST);
        add(body, BorderLayout.CENTER);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { onPick.accept(index); }
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
        };
        for (JComponent c : new JComponent[] {this, body, badge}) { c.addMouseListener(mouse); }
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void setSelected(boolean s) { selected = s; repaint(); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(selected ? Theme.PRIMARY_SOFT : hover ? new Color(0xF0F4FA) : Color.WHITE);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
        g2.setStroke(new BasicStroke(selected ? 2.5f : 1f));
        g2.setColor(selected ? Theme.PRIMARY : Theme.BORDER);
        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
        g2.dispose();
        super.paintComponent(g);
    }
}
