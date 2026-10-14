// Thanh trên cùng sau khi đăng nhập: kỳ thi, sinh viên, đồng hồ đếm ngược và chấm trạng thái kết nối (Người 1).
package exam.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

class TopBar extends JPanel {
    /** Trạng thái kết nối hiện ở chấm màu; NONE = ẩn (chưa đăng nhập hoặc đã xong). */
    enum Conn { NONE, CONNECTED, RECONNECTING, LOST }

    private final JLabel examLabel = Theme.label("Kỳ thi trắc nghiệm", 18, true, Theme.TEXT);
    private final JLabel studentLabel = Theme.label(" ", 14, false, Theme.MUTED);
    private final JLabel timerLabel = Theme.label(" ", 26, true, Theme.SUCCESS);
    private final JLabel connLabel = Theme.label(" ", 14, false, Theme.MUTED);
    private final Dot dot = new Dot();

    TopBar() {
        super(new BorderLayout(16, 0));
        setBackground(Theme.CARD);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
            BorderFactory.createEmptyBorder(10, 24, 10, 24)));
        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.add(examLabel, BorderLayout.NORTH);
        left.add(studentLabel, BorderLayout.SOUTH);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        right.setOpaque(false);
        right.add(timerLabel);
        right.add(dot);
        right.add(connLabel);
        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);
    }

    void setStudent(String name, String id) { studentLabel.setText(name + " - " + id); }

    void setExamTitle(String title) { examLabel.setText(title); }

    void setTimer(int seconds) {
        timerLabel.setText(String.format("Còn lại %02d:%02d", seconds / 60, seconds % 60));
        timerLabel.setForeground(Theme.timerColor(seconds));
    }

    void setTimerVisible(boolean visible) { timerLabel.setVisible(visible); }

    void setConnection(Conn state) {
        dot.setVisible(state != Conn.NONE);
        connLabel.setVisible(state != Conn.NONE);
        switch (state) {
            case CONNECTED -> { dot.color = Theme.SUCCESS; connLabel.setText("Đã kết nối"); }
            case RECONNECTING -> { dot.color = Theme.WARNING; connLabel.setText("Đang kết nối lại..."); }
            case LOST -> { dot.color = Theme.ERROR; connLabel.setText("Mất kết nối"); }
            default -> { }
        }
        repaint();
    }

    private static class Dot extends JComponent {
        Color color = Theme.SUCCESS;

        Dot() { setPreferredSize(new Dimension(12, 12)); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
            g2.dispose();
        }
    }
}
