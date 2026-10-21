// Bảng thí sinh của cửa sổ giám thị (theo ảnh tham khảo): biểu tượng cảnh báo, nhãn trạng thái, thanh bất thường, tô dòng cần chú ý (Người 2).
package exam.server;

import exam.common.Icons;
import exam.common.Palette;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

class RosterTable extends JScrollPane {
    private static final String[] COLUMNS = {"", "Mã SV", "Họ tên", "Máy", "Trạng thái", "Vi phạm", "Vi phạm gần nhất",
        "Bất thường", "Điểm"};
    private static final int[] WIDTHS = {36, 100, 160, 60, 120, 84, 170, 200, 64};

    private static class Model extends AbstractTableModel {
        List<ExamRoom.Row> rows = new ArrayList<>();

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int c) { return COLUMNS[c]; }

        @Override
        public Object getValueAt(int r, int c) {
            ExamRoom.Row row = rows.get(r);
            return switch (c) {
                case 0 -> Roster.needsAttention(row);
                case 1 -> row.studentId();
                case 2 -> row.name();
                case 3 -> Roster.osShort(row.os());
                case 4 -> row.status();
                case 5 -> row.violations();
                case 6 -> Roster.lastViolationText(row);
                case 7 -> row;
                default -> Roster.scoreText(row.score());
            };
        }
    }

    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private boolean updating;

    RosterTable(Consumer<ExamRoom.Row> onSelect, Consumer<ExamRoom.Row> onOpen) {
        table.setRowHeight(Palette.GAP * 6);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(Palette.SEPARATOR);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.setFocusable(false);
        for (int i = 0; i < WIDTHS.length; i++) { table.getColumnModel().getColumn(i).setPreferredWidth(WIDTHS[i]); }
        table.getColumnModel().getColumn(0).setMaxWidth(36);
        table.setDefaultRenderer(Object.class, new CellRenderer());
        table.getColumnModel().getColumn(0).setCellRenderer(new IconRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(new StatusRenderer());
        table.getColumnModel().getColumn(7).setCellRenderer(new AnomalyRenderer());
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new HeaderRenderer());
        header.setPreferredSize(new Dimension(0, 36));
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updating) { onSelect.accept(selected()); }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0) { onOpen.accept(selected()); }
            }
        });
        setViewportView(table);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.SEPARATOR));
        getViewport().setBackground(Color.WHITE);
    }

    ExamRoom.Row selected() {
        int r = table.getSelectedRow();
        return r < 0 ? null : model.rows.get(r);
    }

    /** Thay cả danh sách nhưng giữ nguyên thí sinh đang chọn (mỗi giây làm mới một lần). */
    void setRows(List<ExamRoom.Row> rows) {
        ExamRoom.Row before = selected();
        updating = true;
        model.rows = new ArrayList<>(rows);
        model.fireTableDataChanged();
        if (before != null) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).studentId().equals(before.studentId())) { table.setRowSelectionInterval(i, i); }
            }
        }
        updating = false;
    }

    private static Color background(JTable t, int row, boolean selected) {
        if (selected) { return Palette.SELECTED; }
        ExamRoom.Row r = ((Model) t.getModel()).rows.get(row);
        return Roster.needsAttention(r) ? Palette.ATTENTION : Color.WHITE;
    }

    private static class HeaderRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, v, false, false, r, c);
            setBackground(Color.WHITE);
            setForeground(Palette.MUTED);
            setFont(getFont().deriveFont(Font.BOLD, 12.5f));
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            setHorizontalAlignment(c == 5 || c == 8 ? SwingConstants.RIGHT : SwingConstants.LEFT);
            return this;
        }
    }

    private static class CellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, v, false, false, r, c);
            setBackground(background(t, r, sel));
            setForeground(c == 6 && "-".equals(v) ? Palette.MUTED : Palette.TEXT);
            setFont(getFont().deriveFont(c == 2 ? Font.BOLD : Font.PLAIN));
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            setHorizontalAlignment(c == 5 || c == 8 ? SwingConstants.RIGHT : SwingConstants.LEFT);
            return this;
        }
    }

    private static class IconRenderer implements TableCellRenderer {
        private final JLabel label = new JLabel(Icons.warning(16), SwingConstants.CENTER);

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            label.setIcon(Boolean.TRUE.equals(v) ? Icons.warning(16) : null);
            label.setOpaque(true);
            label.setBackground(background(t, r, sel));
            return label;
        }
    }

    /** Nhãn trạng thái bo tròn: Đang làm xanh lá, Mất kết nối cam, Đã nộp xám. */
    private static class StatusRenderer implements TableCellRenderer {
        private final JPanel cell = new JPanel(new BorderLayout());
        private final StatusPill pill = new StatusPill();

        StatusRenderer() {
            cell.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            JPanel left = new JPanel(new BorderLayout());
            left.setOpaque(false);
            left.add(pill, BorderLayout.WEST);
            cell.add(left, BorderLayout.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            pill.set(Roster.statusText((String) v), (String) v);
            cell.setBackground(background(t, r, sel));
            return cell;
        }
    }

    private static class StatusPill extends JLabel {
        private Color fill = Palette.NEUTRAL_SOFT;

        StatusPill() {
            setFont(getFont().deriveFont(Font.BOLD, 12.5f));
            setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
        }

        void set(String text, String status) {
            setText(text);
            switch (status) {
                case Session.ST_IN_EXAM -> style(Palette.SUCCESS_SOFT, Palette.SUCCESS_TEXT);
                case Session.ST_DISCONNECTED -> style(Palette.WARNING_SOFT, Palette.WARNING_TEXT);
                case Session.ST_SUBMITTED -> style(Palette.NEUTRAL_SOFT, Palette.NEUTRAL_TEXT);
                case Session.ST_PRECHECK_FAIL -> style(Palette.ERROR_SOFT, Palette.ERROR_TEXT);
                case Session.ST_TERMINATED -> style(Palette.ERROR_TEXT, Color.WHITE);
                default -> style(Palette.PRIMARY_SOFT, Palette.PRIMARY_TEXT);
            }
        }

        private void style(Color bg, Color fg) {
            fill = bg;
            setForeground(fg);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Thanh nhỏ + số: máy bị gắn cờ tô cam đậm, còn lại xám. */
    private static class AnomalyRenderer implements TableCellRenderer {
        private final Bar bar = new Bar();
        private final JLabel text = new JLabel();
        private final JPanel cell = new JPanel(new BorderLayout(10, 0));

        AnomalyRenderer() {
            cell.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            bar.setPreferredSize(new Dimension(Palette.GAP * 9, Palette.GAP));
            JPanel barBox = new JPanel(new java.awt.GridBagLayout());
            barBox.setOpaque(false);
            barBox.add(bar);
            cell.add(barBox, BorderLayout.WEST);
            cell.add(text, BorderLayout.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            ExamRoom.Row row = (ExamRoom.Row) v;
            cell.setBackground(background(t, r, sel));
            Double score = row.anomalyScore();
            bar.setVisible(score != null);
            bar.value = score == null ? 0 : score;
            bar.flagged = row.anomaly();
            text.setText(score == null ? "-" : String.format(Locale.ROOT, "%.2f", score));
            text.setForeground(row.anomaly() ? Palette.WARNING_TEXT : Palette.TEXT);
            text.setFont(text.getFont().deriveFont(row.anomaly() ? Font.BOLD : Font.PLAIN));
            return cell;
        }
    }

    private static class Bar extends JComponent {
        double value;
        boolean flagged;

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Palette.TRACK);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.setColor(flagged ? Palette.WARNING : Palette.BAR);
            g2.fillRoundRect(0, 0, (int) Math.max(getHeight(), getWidth() * Math.min(1, value)), getHeight(), getHeight(), getHeight());
            g2.dispose();
        }
    }
}
