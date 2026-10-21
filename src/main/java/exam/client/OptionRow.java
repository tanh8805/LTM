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

/** Một lựa chọn: ô chữ cái xám bo góc bên trái; hàng đang chọn có viền xanh, nền xanh nhạt và ô chữ cái màu chính. */
class OptionRow extends JPanel {
    private boolean selected, hover;
    private final JLabel badge;

    OptionRow(char letter, String text, boolean selected, IntConsumer onPick, int index) {
        super(new BorderLayout(16, 0));
        this.selected = selected;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 18));
        setAlignmentX(LEFT_ALIGNMENT);
        badge = new JLabel(String.valueOf(letter), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(OptionRow.this.selected ? Theme.PRIMARY : Theme.NEUTRAL);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setFont(Theme.font(15, true));
        badge.setForeground(selected ? Color.WHITE : Theme.TEXT);
        badge.setPreferredSize(new Dimension(32, 32));
        JPanel badgeBox = new JPanel(new java.awt.GridBagLayout());
        badgeBox.setOpaque(false);
        badgeBox.add(badge);
        JTextArea body = new JTextArea(text);
        body.setEditable(false);
        body.setFocusable(false);
        body.setOpaque(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setFont(Theme.font(18, false));
        body.setForeground(Theme.TEXT);
        add(badgeBox, BorderLayout.WEST);
        add(body, BorderLayout.CENTER);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { onPick.accept(index); }
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
        };
        for (JComponent c : new JComponent[] {this, body, badge, badgeBox}) { c.addMouseListener(mouse); }
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void setSelected(boolean s) {
        selected = s;
        badge.setForeground(s ? Color.WHITE : Theme.TEXT);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(selected ? Theme.PRIMARY_SOFT : hover ? Theme.BACKGROUND : Color.WHITE);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        g2.setStroke(new BasicStroke(selected ? 1.6f : 1f));
        g2.setColor(selected ? Theme.PRIMARY : Theme.BORDER);
        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12);
        g2.dispose();
        super.paintComponent(g);
    }
}
