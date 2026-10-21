// Hộp thoại lịch sử của một thí sinh: giờ, loại, chi tiết, lấy từ bộ nhớ của server (Người 2).
package exam.server;

import exam.common.Palette;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

class HistoryDialog extends JDialog {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Color ANOMALY_ROW = Palette.ERROR_SOFT;

    private final List<ExamLog.Event> events;

    HistoryDialog(Window owner, String studentId, String name, List<ExamLog.Event> events) {
        super(owner, "Lịch sử: " + studentId + " - " + name, ModalityType.MODELESS);
        this.events = events;
        DefaultTableModel model = new DefaultTableModel(new Object[] {"Giờ", "Loại", "Chi tiết"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (ExamLog.Event e : events) {
            model.addRow(new Object[] {e.time().format(TIME), Roster.typeText(e.type()), e.detail()});
        }
        JTable table = new JTable(model);
        table.setDefaultRenderer(Object.class, new RowRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(130);
        table.getColumnModel().getColumn(2).setPreferredWidth(560);
        fitRowHeights(table);
        add(new JScrollPane(table), BorderLayout.CENTER);
        setSize(820, 420);
        setLocationRelativeTo(owner);
    }

    /** Chi tiết dài (lý do bất thường) xuống dòng chứ không bị cắt, nên mỗi dòng có chiều cao riêng. */
    private void fitRowHeights(JTable table) {
        int detailWidth = table.getColumnModel().getColumn(2).getPreferredWidth();
        for (int row = 0; row < events.size(); row++) {
            RowRenderer cell = new RowRenderer();
            cell.setText(events.get(row).detail());
            cell.setSize(detailWidth, Short.MAX_VALUE);
            table.setRowHeight(row, Math.max(24, cell.getPreferredSize().height + 4));
        }
    }

    private class RowRenderer extends JTextArea implements TableCellRenderer {
        RowRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int col) {
            setText(String.valueOf(value));
            setFont(t.getFont());
            setSize(t.getColumnModel().getColumn(col).getWidth(), Short.MAX_VALUE);
            if (selected) {
                setBackground(t.getSelectionBackground());
                setForeground(t.getSelectionForeground());
                return this;
            }
            setForeground(t.getForeground());
            setBackground(t.getBackground());
            String type = events.get(row).type();
            if (type.equals("VIOLATION")) {
                setBackground(Palette.WARNING_SOFT);
            } else if (type.equals("ANOMALY_ON") || type.equals("TERMINATED")) {
                setBackground(ANOMALY_ROW);
            }
            return this;
        }
    }
}
