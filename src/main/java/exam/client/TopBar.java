// Thanh trên cùng: tên kỳ thi bên trái; trong giờ thi có đồng hồ ở giữa, chấm kết nối và nút Nộp bài bên phải (Người 1).
package exam.client;

import exam.common.Icons;
import exam.common.Protocol;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

class TopBar extends JPanel {
    /** Trạng thái kết nối hiện ở chấm màu; NONE = ẩn (chưa đăng nhập hoặc đã xong). */
    enum Conn { NONE, CONNECTED, RECONNECTING, LOST }

    private final JLabel examLabel = Theme.label(Protocol.TOPIC, 15, true, Theme.TEXT);
    private final JLabel studentLabel = Theme.label(" ", 14, false, Theme.MUTED);
    private final JLabel timerLabel = Theme.label(" ", 16, true, Theme.TEXT);
    private final JLabel connLabel = Theme.label(" ", 14, false, Theme.TEXT);
    private final JButton submitBtn = Theme.primaryButton("Nộp bài");
    private final Dot dot = new Dot();
    private final JPanel timerPill = new Pill(), connBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    private boolean examMode;
    private Conn conn = Conn.NONE;

    TopBar(Runnable onSubmit) {
        super(new GridBagLayout());
        setBackground(Theme.CARD);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
            BorderFactory.createEmptyBorder(10, 24, 10, 24)));
        submitBtn.setMargin(new java.awt.Insets(6, 18, 6, 18));
        submitBtn.addActionListener(e -> onSubmit.run());
        timerPill.add(new JLabel(Icons.clock(16, Theme.TEXT)));
        timerPill.add(timerLabel);
        connBox.setOpaque(false);
        connBox.add(dot);
        connBox.add(connLabel);
        connBox.add(submitBtn);
        GridBagConstraints c = new GridBagConstraints();
        c.weightx = 1; c.anchor = GridBagConstraints.WEST;
        add(examLabel, c);
        c.weightx = 0; c.anchor = GridBagConstraints.CENTER;
        add(timerPill, c);
        c.weightx = 1; c.anchor = GridBagConstraints.EAST;
        JPanel right = new JPanel(new java.awt.CardLayout());
        right.setOpaque(false);
        right.add(studentLabel, "student");
        right.add(connBox, "exam");
        add(right, c);
        timerPill.setVisible(false);
        applyMode();
    }

    private void applyMode() {
        ((java.awt.CardLayout) ((JPanel) connBox.getParent()).getLayout()).show(connBox.getParent(), examMode ? "exam" : "student");
        timerPill.setVisible(examMode);
    }

    void setStudent(String name, String id) { studentLabel.setText(id + " · " + name); }

    void setExamTitle(String title) { examLabel.setText(title); }

    /** Màn làm bài hiện đồng hồ, kết nối và nút Nộp bài; các màn khác chỉ hiện sinh viên. */
    void setExamMode(boolean exam) { examMode = exam; applyMode(); }

    void setTimer(int seconds) {
        timerLabel.setText(String.format("%02d:%02d", seconds / 60, seconds % 60));
        timerLabel.setForeground(seconds < 300 ? Theme.timerColor(seconds) : Theme.TEXT);
    }

    void setConnection(Conn state) {
        conn = state;
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

    /** Hộp bo tròn viền mảnh quanh đồng hồ. */
    private static class Pill extends JPanel {
        Pill() {
            super(new FlowLayout(FlowLayout.CENTER, 8, 0));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(6, 18, 6, 18));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.setColor(Theme.BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.dispose();
        }
    }

    private static class Dot extends JComponent {
        Color color = Theme.SUCCESS;

        Dot() { setPreferredSize(new Dimension(10, 10)); }

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
