package exam.server;

import exam.common.Card;
import exam.common.Palette;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

class StudentsDialog extends JDialog {
    private static final String EMPTY_HINT = "Chưa có danh sách. Bấm Nhập file CSV (cột studentId,name,password)";

    private final ExamRoom room;
    private final Runnable onChanged;
    private final JLabel count = new JLabel();
    private final JLabel hint = new JLabel(EMPTY_HINT);
    private final DefaultTableModel model = new DefaultTableModel(new Object[] {"Mã SV", "Họ tên", "Mật khẩu"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    StudentsDialog(JFrame owner, ExamRoom room, Runnable onChanged) {
        super(owner, "Danh sách sinh viên", true);
        this.room = room;
        this.onChanged = onChanged;

        JTable table = new JTable(model);
        table.setRowHeight(28);
        Card card = new Card();
        card.setLayout(new BorderLayout(0, Palette.GAP));
        card.add(new JScrollPane(table), BorderLayout.CENTER);
        card.add(hint, BorderLayout.SOUTH);
        hint.setForeground(Palette.MUTED);

        JButton importBtn = new JButton("Nhập file CSV...");
        JButton close = new JButton("Đóng");
        importBtn.addActionListener(e -> importStudents());
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Palette.GAP, 0));
        buttons.setOpaque(false);
        buttons.add(importBtn);
        buttons.add(close);

        JPanel root = new JPanel(new BorderLayout(0, Palette.GAP * 2));
        root.setBackground(Palette.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(Palette.GAP * 2, Palette.GAP * 2, Palette.GAP * 2, Palette.GAP * 2));
        root.add(count, BorderLayout.NORTH);
        root.add(card, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        reload();
        setSize(640, 520);
        setLocationRelativeTo(owner);
    }

    private void reload() {
        model.setRowCount(0);
        for (StudentList.Account a : room.studentAccounts()) {
            model.addRow(new Object[] {a.id(), a.name(), a.password()});
        }
        count.setText("Đang có " + model.getRowCount() + " sinh viên");
        hint.setVisible(model.getRowCount() == 0);
    }

    /** Chọn file CSV, kiểm tra từng dòng; đạt thì chỉ nạp vào phòng (server không lưu danh sách ra file). */
    private void importStudents() {
        if (room.isStarted()) {
            JOptionPane.showMessageDialog(this, "Chỉ nạp danh sách khi chưa bắt đầu thi");
            return;
        }
        JFileChooser chooser = new JFileChooser(".");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) { return; }
        try {
            StudentList list = StudentList.load(chooser.getSelectedFile().toPath());
            room.setStudents(list);
            reload();
            onChanged.run();
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Danh sách sinh viên có lỗi", JOptionPane.ERROR_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Không đọc hoặc ghi được danh sách", JOptionPane.ERROR_MESSAGE);
        }
    }
}
