// Khung chi tiết bên phải cửa sổ giám thị: ba ô số liệu và các sự kiện đáng chú ý của thí sinh đang chọn (Người 2).
package exam.server;

import exam.common.Palette;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

class DetailPanel extends JPanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    static final String NOTE = "Điểm bất thường chỉ cho biết máy này khác số đông trong phòng. Giám thị là người quyết định có gian lận hay không.";

    private final JPanel body = new JPanel();

    DetailPanel() {
        super(new BorderLayout());
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(310, 0));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Palette.SEPARATOR));
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(22, 20, 12, 20));
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        add(scroll, BorderLayout.CENTER);
        JLabel note = new JLabel(wrap(NOTE));
        note.setForeground(Palette.MUTED);
        note.setFont(note.getFont().deriveFont(12.5f));
        note.setBorder(BorderFactory.createEmptyBorder(8, 20, 18, 20));
        add(note, BorderLayout.SOUTH);
        show(null, List.of());
    }

    /** row = null: chưa chọn ai. events là toàn bộ lịch sử của thí sinh; chỉ các sự kiện đáng chú ý được liệt kê, mới nhất trước. */
    void show(ExamRoom.Row row, List<ExamLog.Event> events) {
        body.removeAll();
        if (row == null) {
            body.add(text("Chọn một thí sinh", 17, true, Palette.TEXT));
            body.add(text(wrap("Bấm vào một dòng trong bảng để xem chi tiết."), 13, false, Palette.MUTED));
        } else {
            body.add(text(wrap(row.name()), 20, true, Palette.TEXT));
            body.add(text(row.studentId() + (row.os().isEmpty() ? "" : " · " + row.os()), 13, false, Palette.MUTED));
            body.add(Box.createVerticalStrut(16));
            JPanel stats = new JPanel(new GridLayout(1, 3, 8, 0));
            stats.setOpaque(false);
            stats.setAlignmentX(LEFT_ALIGNMENT);
            stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
            stats.add(stat("Vi phạm", String.valueOf(row.violations())));
            stats.add(stat("Rời cửa sổ", String.valueOf(row.focusLost())));
            stats.add(stat("Bất thường", row.anomalyScore() == null ? "-" : String.format(Locale.ROOT, "%.2f", row.anomalyScore())));
            body.add(stats);
            body.add(Box.createVerticalStrut(20));
            body.add(text("SỰ KIỆN", 12, true, Palette.MUTED));
            body.add(Box.createVerticalStrut(8));
            List<ExamLog.Event> notable = new ArrayList<>(events.stream().filter(Roster::notable).toList());
            if (notable.isEmpty()) {
                body.add(text("Chưa có sự kiện", 14, true, Palette.TEXT));
                body.add(text(wrap("Máy này không có gì đáng chú ý."), 13, false, Palette.MUTED));
            }
            for (int i = notable.size() - 1; i >= 0; i--) {
                ExamLog.Event e = notable.get(i);
                body.add(text(e.time().format(TIME), 12, false, Palette.MUTED));
                body.add(text(wrap(Roster.eventText(e)), 14, false, Palette.TEXT));
                body.add(Box.createVerticalStrut(8));
            }
        }
        body.revalidate();
        body.repaint();
    }

    /** Chữ tự xuống dòng trong bề ngang của khung (JLabel không tự ngắt dòng nếu không dùng HTML). */
    private static String wrap(String s) {
        return "<html><body style='width:245px'>" + s.replace("&", "&amp;").replace("<", "&lt;") + "</body></html>";
    }

    private static JLabel text(String text, float size, boolean bold, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(bold ? java.awt.Font.BOLD : java.awt.Font.PLAIN, size));
        l.setForeground(color);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private static JPanel stat(String caption, String value) {
        JPanel p = new JPanel(new BorderLayout(0, 2)) {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(Palette.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
        p.add(text(caption, 12, false, Palette.MUTED), BorderLayout.NORTH);
        p.add(text(value, 20, true, Palette.TEXT), BorderLayout.CENTER);
        return p;
    }
}
