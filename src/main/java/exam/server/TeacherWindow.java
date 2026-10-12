// Giao diện giám thị (Swing): chọn đề, bắt đầu/kết thúc thi, bảng sinh viên làm mới mỗi giây và ô log.
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
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

public class TeacherWindow extends JFrame {
    private static final Color WARN = new Color(255, 205, 205);
    private static final String[] COLUMNS = {"Mã SV", "Họ tên", "Trạng thái", "Số vi phạm", "Vi phạm gần nhất",
        "Bất thường", "Điểm"};

    /** Bảng dữ liệu lấy từ server.rows(); mỗi giây thay cả danh sách cho đơn giản. */
    private static class RowsModel extends AbstractTableModel {
        List<ExamServer.Row> rows = new ArrayList<>();

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int c) { return COLUMNS[c]; }

        @Override
        public Object getValueAt(int r, int c) {
            ExamServer.Row row = rows.get(r);
            return switch (c) {
                case 0 -> row.studentId();
                case 1 -> row.name();
                case 2 -> row.status();
                case 3 -> row.violations();
                case 4 -> row.lastViolation();
                case 5 -> row.anomalyScore() == null ? "-" : String.format("%.2f", row.anomalyScore());
                default -> row.score() == null ? "-" : row.score().toString();
            };
        }
    }

    private final ExamServer server;
    private final RowsModel model = new RowsModel();
    private final JLabel examLabel = new JLabel("Chưa chọn đề"), timeLabel = new JLabel("Còn lại: -");
    private final JButton startBtn = new JButton("Bắt đầu thi"), endBtn = new JButton("Kết thúc thi");
    private final JTextArea logArea = new JTextArea(8, 60);

    public TeacherWindow(ExamServer server) {
        super("Giám thị - Server thi");
        this.server = server;
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
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, focus, r, c);
                ExamServer.Row row = model.rows.get(t.convertRowIndexToModel(r));
                boolean warn = row.violations() > 0 || row.anomaly();
                comp.setBackground(sel ? t.getSelectionBackground() : warn ? WARN : Color.WHITE);
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
        String title = server.examTitle();
        examLabel.setText(title == null ? "Chưa chọn đề" : "Đề: " + title);
        model.rows = server.rows();
        model.fireTableDataChanged();
        boolean started = server.isStarted();
        startBtn.setEnabled(server.examTitle() != null && !started);
        endBtn.setEnabled(started && !server.isEnded());
        if (started) {
            int s = server.remainingSeconds();
            timeLabel.setText(String.format("Còn lại: %02d:%02d", s / 60, s % 60));
        }
    }

    private void chooseExam() {
        JFileChooser chooser = new JFileChooser(new File("exams"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            server.loadExam(chooser.getSelectedFile().toPath());
            refresh();
        } catch (IllegalArgumentException | IllegalStateException | IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Không nạp được đề", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void startExam() {
        try {
            server.startExam();
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
        }
        refresh();
    }

    private void endExam() {
        if (JOptionPane.showConfirmDialog(this, "Kết thúc bài thi ngay?", "Xác nhận", JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION) {
            server.endExam("Giáo viên kết thúc");
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
