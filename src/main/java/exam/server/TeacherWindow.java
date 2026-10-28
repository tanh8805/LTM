// Giao diện giám thị (Swing + FlatLaf, theo ảnh tham khảo): thanh trên, tab lọc và ô tìm kiếm, bảng thí sinh, khung chi tiết, nhật ký (Người 2).
package exam.server;

import exam.common.Card;
import exam.common.Icons;
import exam.common.Palette;
import exam.common.Protocol;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class TeacherWindow extends JFrame {
    private final ExamServer server;
    private final ExamRoom room;
    private final JButton chooseBtn = new JButton("Mở đề..."), actionBtn = new JButton();
    private final JLabel titleLabel = label(Protocol.TOPIC, 16, true, Palette.PRIMARY), subLabel = label(" ", 12.5f, false, Palette.MUTED),
        timeValue = label("-", 15, true, Palette.TEXT),
        noListWarning = label("Chưa nạp danh sách sinh viên: ai cũng đăng nhập được", 13, true, Palette.WARNING_TEXT);
    private final SideBar sideBar = new SideBar();
    private final JTextArea logArea = new JTextArea();
    private final DetailPanel detail = new DetailPanel(this::setAppAllowed);
    private final JTextField search = new JTextField(22);
    private final Map<Roster.Tab, TabButton> tabs = new EnumMap<>(Roster.Tab.class);
    private final RosterTable table = new RosterTable(row -> showDetail(row), this::openHistory);
    private Roster.Tab tab = Roster.Tab.ALL;

    public TeacherWindow(ExamServer server) {
        super("Giám thị - " + Protocol.TOPIC);
        this.server = server;
        this.room = server.room();
        server.setListener(line -> SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());    // tự cuộn xuống dòng mới nhất
        }));
        chooseBtn.addActionListener(e -> chooseExam());
        actionBtn.addActionListener(e -> onAction());
        actionBtn.setForeground(Color.WHITE);
        actionBtn.setFont(actionBtn.getFont().deriveFont(Font.BOLD));
        actionBtn.setMargin(new Insets(7, 18, 7, 18));
        getContentPane().setBackground(Palette.BACKGROUND);

        noListWarning.setOpaque(true);
        noListWarning.setBackground(Palette.WARNING_SOFT);
        noListWarning.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        JPanel north = new JPanel(new BorderLayout());
        north.add(topBar(), BorderLayout.NORTH);
        north.add(noListWarning, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        sideBar.addItem("monitor", "Giám sát", true, () -> { });
        sideBar.addItem("students", "Sinh viên", false, () -> new StudentsDialog(this, room, this::refresh).setVisible(true));
        sideBar.addItem("exams", "Đề thi", false, () -> { new ExamBankDialog(this, room, ExamBank.DIR).setVisible(true); refresh(); });
        sideBar.addItem("rules", "Ứng dụng", false, () -> new RulesDialog(this, server).setVisible(true));
        sideBar.addItem("results", "Kết quả", false, this::openResults);
        add(sideBar, BorderLayout.WEST);
        add(content(), BorderLayout.CENTER);

        setSize(1440, 820);
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

    private static JLabel label(String text, float size, boolean bold, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(color);
        return l;
    }

    /** Thẻ trắng viền mảnh bọc một thành phần. */
    private static JPanel card(JComponent inner) {
        Card card = new Card();
        card.setLayout(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        card.add(inner, BorderLayout.CENTER);
        return card;
    }

    /** Vùng nội dung trên nền xám: thẻ bảng thí sinh, thẻ nhật ký bên dưới, thẻ chi tiết bên phải. */
    private JPanel content() {
        int gap = Palette.GAP * 2;
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBorder(BorderFactory.createEmptyBorder(0, 14, 8, 14));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(null);
        logScroll.setPreferredSize(new Dimension(0, 150));
        JLabel logTitle = label("Nhật ký", 13, true, Palette.MUTED);
        logTitle.setBorder(BorderFactory.createEmptyBorder(8, 14, 4, 14));
        JPanel logBox = new JPanel(new BorderLayout());
        logBox.setOpaque(false);
        logBox.add(logTitle, BorderLayout.NORTH);
        logBox.add(logScroll, BorderLayout.CENTER);
        JPanel tableBox = new JPanel(new BorderLayout());
        tableBox.setOpaque(false);
        tableBox.add(filterBar(), BorderLayout.NORTH);
        tableBox.add(table, BorderLayout.CENTER);

        JPanel left = new JPanel(new BorderLayout(0, gap));
        left.setOpaque(false);
        left.add(card(tableBox), BorderLayout.CENTER);
        left.add(card(logBox), BorderLayout.SOUTH);
        JPanel content = new JPanel(new BorderLayout(gap, 0));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(gap, gap, gap, gap));
        content.add(left, BorderLayout.CENTER);
        content.add(card(detail), BorderLayout.EAST);
        return content;
    }

    /** Thanh trên cùng: tên đề tài và đề đang mở, đồng hồ ở giữa, IP LAN và nút Bắt đầu/Kết thúc bên phải. */
    private JPanel topBar() {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setBackground(Color.WHITE);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Palette.BORDER),
            BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        JPanel titles = new JPanel(new BorderLayout());
        titles.setOpaque(false);
        titles.add(titleLabel, BorderLayout.NORTH);
        titles.add(subLabel, BorderLayout.SOUTH);
        left.add(titles);
        left.add(chooseBtn);
        Pill clock = new Pill(Color.WHITE, Palette.BORDER, 18, 6);
        clock.add(label("Còn lại", 13, false, Palette.MUTED));
        clock.add(timeValue);
        Pill address = new Pill(Palette.NEUTRAL_SOFT, null, 12, 6);
        String ips = LocalAddresses.list();
        address.setToolTipText("Các IP LAN của máy chủ (kể cả card ZeroTier): " + ips);
        address.add(label("IP LAN", 13, false, Palette.MUTED));
        JLabel ip = label(ips + " · cổng " + Protocol.TCP_PORT, 13, true, Palette.TEXT);
        ip.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        address.add(ip);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        right.add(address);
        right.add(actionBtn);
        GridBagConstraints c = new GridBagConstraints();
        c.weightx = 1; c.anchor = GridBagConstraints.WEST;
        bar.add(left, c);
        c.weightx = 0; c.anchor = GridBagConstraints.CENTER;
        bar.add(clock, c);
        c.weightx = 1; c.anchor = GridBagConstraints.EAST;
        bar.add(right, c);
        return bar;
    }

    /** Hàng tab lọc bên trái và ô tìm kiếm bên phải. */
    private JPanel filterBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        Pill group = new Pill(Palette.NEUTRAL_SOFT, null, 4, 3);
        group.setLayout(new FlowLayout(FlowLayout.LEFT, 2, 0));
        ButtonGroup buttons = new ButtonGroup();
        for (Roster.Tab t : Roster.Tab.values()) {
            TabButton b = new TabButton();
            b.setSelected(t == tab);
            b.addActionListener(e -> { tab = t; refresh(); });
            buttons.add(b);
            tabs.put(t, b);
            group.add(b);
        }
        search.putClientProperty("JTextField.placeholderText", "Tìm mã SV hoặc tên");
        search.putClientProperty("JTextField.leadingIcon", Icons.search(16, Palette.MUTED));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refresh(); }
            @Override public void removeUpdate(DocumentEvent e) { refresh(); }
            @Override public void changedUpdate(DocumentEvent e) { refresh(); }
        });
        JPanel searchBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        searchBox.setOpaque(false);
        searchBox.add(search);
        bar.add(group, BorderLayout.WEST);
        bar.add(searchBox, BorderLayout.EAST);
        return bar;
    }

    private void refresh() {
        ExamRoom.Summary sum = room.summary();
        boolean started = room.isStarted(), ended = room.isEnded();
        subLabel.setText(sum == null ? "Đề thi: chưa chọn (bấm Mở đề... hoặc mục Đề thi)"
            : "Đề thi: " + sum.title() + " · " + sum.questions() + " câu · " + sum.minutes() + " phút · "
            + (started ? "bắt đầu " + sum.startedAt() : "chưa bắt đầu"));
        List<ExamRoom.Row> all = room.rosterRows();
        for (Roster.Tab t : Roster.Tab.values()) { tabs.get(t).setText(t.label + " " + Roster.count(all, t)); }
        table.setRows(Roster.filter(all, tab, search.getText()));
        showDetail(table.selected());
        int students = room.studentCount();
        sideBar.setText("students", students == 0 ? "Sinh viên" : "Sinh viên (" + students + ")");
        noListWarning.setVisible(students == 0);
        sideBar.setEnabled("exams", !started);
        chooseBtn.setEnabled(!started);                    // chỉ đổi được đề khi bài thi chưa bắt đầu (kể cả sau Kỳ thi mới)
        if (ended) {
            timeValue.setText("Đã kết thúc");
        } else if (started) {
            int s = room.remainingSeconds();
            timeValue.setText(String.format("%02d:%02d", s / 60, s % 60));
        } else {
            timeValue.setText("-");
        }
        actionBtn.setText(ended ? "Kỳ thi mới" : started ? "Kết thúc thi" : "Bắt đầu thi");
        actionBtn.setEnabled(ended || started || sum != null);
        actionBtn.setBackground(!actionBtn.isEnabled() ? Palette.DISABLED : started && !ended ? Palette.ERROR : Palette.PRIMARY);
    }

    /** Chuột phải vào một app trong khung chi tiết: cho phép hoặc cấm app đó cho cả phòng. */
    private void setAppAllowed(String appName, boolean allow) {
        try {
            server.applyRules(RulesFile.setAppAllowed(room.rules(), appName, allow));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Không ghi được rules.json", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showDetail(ExamRoom.Row row) {
        if (row == null || row.status().equals(Session.ST_ABSENT)) {
            detail.show(row, List.of(), null);      // chưa vào phòng thì không có lịch sử hay app để xem
            return;
        }
        detail.show(row, room.history(row.studentId()), room.appsOf(row.studentId()));
    }

    private void openHistory(ExamRoom.Row row) {
        if (row != null) { new HistoryDialog(this, row.studentId(), row.name(), room.history(row.studentId())).setVisible(true); }
    }

    /** Một nút duy nhất đổi nghĩa theo trạng thái: Bắt đầu thi, Kết thúc thi, Kỳ thi mới. */
    private void onAction() {
        if (room.isEnded()) {
            confirm("Bắt đầu kỳ thi mới?\nMọi máy còn lại sẽ bị ngắt kết nối và danh sách thí sinh bị xoá.\n"
                + "Kết quả kỳ này vẫn nằm trong thư mục riêng của nó.", room::newSession);
        } else if (room.isStarted()) {
            confirm("Kết thúc bài thi ngay?", () -> room.endExam("Giáo viên kết thúc"));
        } else {
            try {
                room.startExam();
            } catch (IllegalStateException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
            }
            refresh();
        }
    }

    private void confirm(String question, Runnable action) {
        if (JOptionPane.showConfirmDialog(this, question, "Xác nhận", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) { return; }
        try {
            action.run();
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
        }
        refresh();
    }

    private void chooseExam() {
        JFileChooser chooser = new JFileChooser(new File("exams"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) { return; }
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
}
