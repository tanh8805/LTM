// Giao diện giám thị (Swing): chọn đề, bắt đầu/kết thúc thi, bảng sinh viên làm mới mỗi giây và ô log (Người 2).
package exam.server;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final JButton startBtn = new JButton("Bắt đầu thi"), endBtn = new JButton("Kết thúc thi"),
        openBtn = new JButton("Mở thư mục kết quả"), newBtn = new JButton("Kỳ thi mới"),
        chooseBtn = new JButton("Chọn file đề...");
    private final JTextArea logArea = new JTextArea(8, 60);

    public TeacherWindow(ExamServer server) {
        super("Giám thị - Server thi");
        this.server = server;
        this.room = server.room();
        server.setListener(line -> SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());    // tự cuộn xuống dòng mới nhất
        }));
        chooseBtn.addActionListener(e -> chooseExam());
        startBtn.addActionListener(e -> startExam());
        endBtn.addActionListener(e -> endExam());
        newBtn.addActionListener(e -> newSession());
        openBtn.addActionListener(e -> openResults());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(chooseBtn);
        top.add(examLabel);
        top.add(startBtn);
        top.add(endBtn);
        top.add(newBtn);
        top.add(openBtn);
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
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int r = table.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && r >= 0) {
                    ExamRoom.Row row = model.rows.get(table.convertRowIndexToModel(r));
                    new HistoryDialog(TeacherWindow.this, row.studentId(), row.name(), room.history(row.studentId())).setVisible(true);
                }
            }
        });
        logArea.setEditable(false);
        add(top, BorderLayout.NORTH);
        add(new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(logArea)),
            BorderLayout.CENTER);
        setSize(1260, 680);
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
        chooseBtn.setEnabled(!started);                    // chỉ đổi được đề khi bài thi chưa bắt đầu (kể cả sau Kỳ thi mới)
        newBtn.setEnabled(room.isEnded());
        if (room.isEnded()) {
            timeLabel.setText("Đã kết thúc");
        } else if (started) {
            int s = room.remainingSeconds();
            timeLabel.setText(String.format("Còn lại: %02d:%02d", s / 60, s % 60));
        } else {
            timeLabel.setText("Còn lại: -");
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

    /** Mở thư mục của kỳ thi (hoặc thư mục gốc nếu chưa bắt đầu); máy không có trình quản lý file thì hiện đường dẫn. */
    private void openResults() {
        Path dir = room.examDir() != null ? room.examDir() : room.resultsRoot();
        try {
            Files.createDirectories(dir);
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new UnsupportedOperationException();
            }
            Desktop.getDesktop().open(dir.toFile());
        } catch (IOException | UnsupportedOperationException | SecurityException ex) {
            JOptionPane.showMessageDialog(this, "Không mở được thư mục. Đường dẫn:\n" + dir.toAbsolutePath());
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

    private void newSession() {
        if (JOptionPane.showConfirmDialog(this, "Bắt đầu kỳ thi mới?\nMọi máy còn lại sẽ bị ngắt kết nối và danh sách thí sinh bị xoá.\n"
                + "Kết quả kỳ này vẫn nằm trong thư mục riêng của nó.", "Xác nhận", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            try {
                room.newSession();
            } catch (IllegalStateException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
            }
            refresh();
        }
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
