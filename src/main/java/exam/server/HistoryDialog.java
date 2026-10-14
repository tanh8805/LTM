// Hộp thoại lịch sử của một thí sinh: giờ, loại, chi tiết, lấy từ bộ nhớ của server (Người 2).
package exam.server;

import java.awt.BorderLayout;
import java.awt.Window;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

class HistoryDialog extends JDialog {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    HistoryDialog(Window owner, String studentId, String name, List<ExamLog.Event> events) {
        super(owner, "Lịch sử: " + studentId + " - " + name, ModalityType.MODELESS);
        DefaultTableModel model = new DefaultTableModel(new Object[] {"Giờ", "Loại", "Chi tiết"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (ExamLog.Event e : events) {
            model.addRow(new Object[] {e.time().format(TIME), e.type(), e.detail()});
        }
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(130);
        table.getColumnModel().getColumn(2).setPreferredWidth(560);
        add(new JScrollPane(table), BorderLayout.CENTER);
        setSize(820, 420);
        setLocationRelativeTo(owner);
    }
}
