// Khung chi tiết bên phải cửa sổ giám thị: ba ô số liệu và các sự kiện đáng chú ý của thí sinh đang chọn (Người 2).
package exam.server;

import com.formdev.flatlaf.util.UIScale;
import exam.common.Html;
import exam.common.Palette;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

class DetailPanel extends JPanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    static final String NOTE = "Điểm bất thường chỉ cho biết máy này khác số đông trong phòng. Giám thị là người quyết định có gian lận hay không.";

    private final JPanel body = new JPanel();
    private final BiConsumer<String, Boolean> onAppRule;      // (tên app, true = cho phép / false = cấm)

    DetailPanel(BiConsumer<String, Boolean> onAppRule) {
        super(new BorderLayout());
        this.onAppRule = onAppRule;
        setOpaque(false);
        setPreferredSize(new Dimension(UIScale.scale(340), 0));    // chữ HTML tự co giãn theo màn hình nên kích thước cũng phải co giãn
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
        show(null, List.of(), null);
    }

    /** row = null: chưa chọn ai. events là toàn bộ lịch sử của thí sinh; chỉ các sự kiện đáng chú ý được liệt kê, mới nhất trước. */
    void show(ExamRoom.Row row, List<ExamLog.Event> events, ExamRoom.AppsView apps) {
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
            stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIScale.scale(66)));
            stats.setPreferredSize(new Dimension(UIScale.scale(295), UIScale.scale(66)));
            stats.add(stat("Vi phạm", String.valueOf(row.violations())));
            stats.add(stat("Rời cửa sổ", String.valueOf(row.focusLost())));
            stats.add(stat("Bất thường", row.anomalyScore() == null ? "-" : String.format(Locale.ROOT, "%.2f", row.anomalyScore())));
            body.add(stats);
            body.add(Box.createVerticalStrut(20));
            addApps(apps);
            body.add(text("SỰ KIỆN", 12, true, Palette.MUTED));
            body.add(Box.createVerticalStrut(8));
            List<ExamLog.Event> notable = Roster.notableEvents(events);
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

    /** Mỗi app một dòng: đỏ = bị cấm, xanh = được phép, xám = chưa có trong danh sách nào. */
    private void addApps(ExamRoom.AppsView apps) {
        boolean received = apps != null && !apps.updatedAt().isEmpty();
        body.add(text(received ? "ỨNG DỤNG ĐANG MỞ (cập nhật " + apps.updatedAt() + ")" : "ỨNG DỤNG ĐANG MỞ", 12, true, Palette.MUTED));
        body.add(Box.createVerticalStrut(8));
        if (!received) {
            body.add(text(wrap("Chưa nhận danh sách từ máy này."), 13, false, Palette.MUTED));
        } else if (apps.apps().isEmpty()) {
            body.add(text(wrap("Không có ứng dụng nào có cửa sổ."), 13, false, Palette.MUTED));
        }
        if (received) {
            for (ExamRoom.AppLine app : apps.apps()) {
                String title = app.title().isEmpty() ? "" : " · " + app.title();
                JLabel line = text(wrap(app.name() + title), 13, false, colorOf(app.kind()));
                line.setComponentPopupMenu(appMenu(app.name()));
                body.add(line);
                body.add(Box.createVerticalStrut(4));
            }
        }
        body.add(Box.createVerticalStrut(16));
    }

    private JPopupMenu appMenu(String appName) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem allow = new JMenuItem("Cho phép ứng dụng này"), ban = new JMenuItem("Cấm ứng dụng này");
        allow.addActionListener(e -> onAppRule.accept(appName, true));
        ban.addActionListener(e -> onAppRule.accept(appName, false));
        menu.add(allow);
        menu.add(ban);
        return menu;
    }

    private static Color colorOf(ExamRoom.AppKind kind) {
        return switch (kind) {
            case BLOCKED -> Palette.ERROR;
            case ALLOWED -> Palette.SUCCESS;
            case OTHER -> Palette.MUTED;
        };
    }

    /** Chữ tự xuống dòng trong bề ngang của khung (JLabel không tự ngắt dòng nếu không dùng HTML). */
    private static String wrap(String s) { return Html.wrap(s, 290); }

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
        p.setBorder(BorderFactory.createEmptyBorder(8, 9, 8, 9));
        p.add(text(caption, 12, false, Palette.MUTED), BorderLayout.NORTH);
        p.add(text(value, 20, true, Palette.TEXT), BorderLayout.CENTER);
        return p;
    }
}
