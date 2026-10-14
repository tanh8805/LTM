// Giao diện giám thị (Swing): chọn đề, bắt đầu/kết thúc thi, bảng sinh viên làm mới mỗi giây và ô log (Người 2).
package exam.server;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

public class TeacherWindow extends JFrame {
    private static final Color FLAGGED = new Color(255, 218, 218), VIOLATED = new Color(255, 236, 204);   // bị gắn cờ ML: đỏ nhạt; có vi phạm: cam nhạt
    private static final java.util.Set<Integer> CENTERED_COLUMNS = java.util.Set.of(3, 5, 6, 7);   // các cột số
    private static final String[] COLUMNS = {"Mã SV", "Họ tên", "Trạng thái", "Số vi phạm", "Vi phạm gần nhất",
        "Mẫu", "Bất thường", "Điểm"};

    /** Bảng dữ liệu lấy từ room.rows(); mỗi giây thay cả danh sách cho đơn giản. */
    private static class RowsModel extends AbstractTableModel {
        List<ExamRoom.Row> rows = new ArrayList<>();

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int c) { return COLUMNS[c]; }

        @Override
        public Object getValueAt(int r, int c) {
            ExamRoom.Row row = rows.get(r);
            return switch (c) {
                case 0 -> row.studentId();
                case 1 -> row.name();
                case 2 -> row.status();
                case 3 -> row.violations();
                case 4 -> row.lastViolation();
                case 5 -> row.samples();
                case 6 -> row.anomalyScore() == null ? "-" : String.format("%.2f", row.anomalyScore());
                default -> row.score() == null ? "-" : row.score().toString();
            };
        }
    }

    private final ExamServer server;
    private final ExamRoom room;
    private final RowsModel model = new RowsModel();
    private final JLabel examLabel = new JLabel("Chưa chọn đề"), timeLabel = new JLabel("Còn lại: -");
    private final JButton startBtn = new JButton("Bắt đầu thi"), endBtn = new JButton("Kết thúc thi");
    private final JTextArea logArea = new JTextArea(8, 60);

    public TeacherWindow(ExamServer server) {
        super("Giám thị - Server thi");
        this.server = server;
        this.room = server.room();
        server.setListener(line -> SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());    // tự cuộn xuống dòng mới nhất
        }));
        JButton chooseBtn = new JButton("Chọn file đề...");
        chooseBtn.addActionListener(e -> chooseExam());
        startBtn.addActionListener(e -> startExam());
        endBtn.addActionListener(e -> endExam());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(chooseBtn);
        top.add(examLabel);
        top.add(startBtn);
        top.add(endBtn);
        top.add(timeLabel);
        top.add(new JLabel("IP máy chủ: " + localAddresses()));

        JTable table = new JTable(model);
        table.setRowHeight(26);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, focus, r, c);
                ExamRoom.Row row = model.rows.get(t.convertRowIndexToModel(r));
                setHorizontalAlignment(CENTERED_COLUMNS.contains(c) ? SwingConstants.CENTER : SwingConstants.LEFT);
                comp.setBackground(sel ? t.getSelectionBackground() : row.anomaly() ? FLAGGED
                    : row.violations() > 0 ? VIOLATED : Color.WHITE);
                return comp;
            }
        });
        logArea.setEditable(false);
        add(top, BorderLayout.NORTH);
        add(new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(logArea)),
            BorderLayout.CENTER);
        setSize(1000, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                server.stop();
                System.exit(0);
            }
        });
        new Timer(1000, e -> refresh()).start();
        refresh();
    }

    private void refresh() {
        String title = room.examTitle();
        examLabel.setText(title == null ? "Chưa chọn đề" : "Đề: " + title);
        model.rows = room.rows();
        model.fireTableDataChanged();
        boolean started = room.isStarted();
        startBtn.setEnabled(room.examTitle() != null && !started);
        endBtn.setEnabled(started && !room.isEnded());
        if (room.isEnded()) {
            timeLabel.setText("Đã kết thúc");
        } else if (started) {
            int s = room.remainingSeconds();
            timeLabel.setText(String.format("Còn lại: %02d:%02d", s / 60, s % 60));
        }
    }

    private void chooseExam() {
        JFileChooser chooser = new JFileChooser(new File("exams"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            room.loadExam(chooser.getSelectedFile().toPath());
            refresh();
        } catch (IllegalArgumentException | IllegalStateException | IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Không nạp được đề", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void startExam() {
        try {
            room.startExam();
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
        }
        refresh();
    }

    private void endExam() {
        if (JOptionPane.showConfirmDialog(this, "Kết thúc bài thi ngay?", "Xác nhận", JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION) {
            room.endExam("Giáo viên kết thúc");
            refresh();
        }
    }

    /** IPv4 không phải loopback của mọi card đang bật, để sinh viên biết nhập IP nào. */
    private static String localAddresses() {
        List<String> ips = new ArrayList<>();
        try {
            for (NetworkInterface card : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!card.isUp()) {
                    continue;
                }
                for (InetAddress a : Collections.list(card.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        ips.add(a.getHostAddress());
                    }
                }
            }
        } catch (IOException e) {
            return "(không đọc được)";
        }
        return ips.isEmpty() ? "(không có)" : String.join(", ", ips);
    }
}
