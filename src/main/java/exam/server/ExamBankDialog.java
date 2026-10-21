package exam.server;

import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;

/** Quản lý đề: danh sách file trong exams/ bên trái, soạn tên, thời gian và câu hỏi bên phải. */
class ExamBankDialog extends JDialog {
    private final ExamRoom room;
    private final Path dir;
    private final List<Path> files = new ArrayList<>();
    private final JList<String> fileList = new JList<>();
    private final JTextField titleField = new JTextField(30);
    private final JSpinner minutes = new JSpinner(new SpinnerNumberModel(15, 1, 600, 1));
    private final List<ExamBank.Question> questions = new ArrayList<>();
    private final AbstractTableModel tableModel = new QuestionTable();
    private final JTable table = new JTable(tableModel);
    private Path current;                    // file đang soạn; null = đề mới chưa lưu
    private boolean dirty, loading;

    ExamBankDialog(JFrame owner, ExamRoom room, Path dir) {
        super(owner, "Quản lý đề", true);
        this.room = room;
        this.dir = dir;
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting() && !loading) { openSelected(); } });
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(26);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) { editQuestion(); } }
        });
        titleField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { dirty = !loading; }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { dirty = !loading; }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { dirty = !loading; }
        });
        minutes.addChangeListener(e -> dirty = !loading);

        add(leftPanel(), BorderLayout.WEST);
        add(rightPanel(), BorderLayout.CENTER);
        setSize(980, 560);
        setLocationRelativeTo(owner);
        refreshFiles(null);
    }

    private JPanel leftPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 6));
        p.add(new JLabel("Các file đề trong " + dir), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(fileList);
        scroll.setPreferredSize(new java.awt.Dimension(240, 0));
        p.add(scroll, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new java.awt.GridLayout(3, 1, 0, 6));
        buttons.add(button("Tạo đề mới", this::newExam));
        buttons.add(button("Nhân bản", this::duplicate));
        buttons.add(button("Xoá", this::delete));
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    private JPanel rightPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(12, 6, 12, 12));
        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        head.add(new JLabel("Tên đề"));
        head.add(titleField);
        head.add(new JLabel("Thời gian (phút)"));
        head.add(minutes);
        JPanel rowButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        rowButtons.add(button("Thêm", this::addQuestion));
        rowButtons.add(button("Sửa", this::editQuestion));
        rowButtons.add(button("Xoá", this::removeQuestion));
        rowButtons.add(button("Lên", () -> move(-1)));
        rowButtons.add(button("Xuống", () -> move(1)));
        rowButtons.add(button("Nhập từ CSV...", this::importCsv));
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bottom.add(button("Dùng đề này", this::useExam));
        bottom.add(button("Lưu", this::save));
        bottom.add(button("Đóng", this::close));
        JPanel south = new JPanel(new BorderLayout());
        south.add(rowButtons, BorderLayout.WEST);
        south.add(bottom, BorderLayout.EAST);
        p.add(head, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    // ---------- File đề ----------

    private void refreshFiles(Path select) {
        files.clear();
        try (Stream<Path> stream = Files.isDirectory(dir) ? Files.list(dir) : Stream.<Path>empty()) {
            stream.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().forEach(files::add);
        } catch (IOException e) {
            error("Không đọc được thư mục " + dir + ": " + e.getMessage());
        }
        loading = true;
        fileList.setListData(files.stream().map(f -> f.getFileName().toString()).toArray(String[]::new));
        if (select != null) { fileList.setSelectedIndex(files.indexOf(select)); }
        loading = false;
    }

    private void openSelected() {
        int index = fileList.getSelectedIndex();
        if (index < 0) { return; }
        if (!confirmDiscard()) {
            loading = true;
            fileList.setSelectedIndex(current == null ? -1 : files.indexOf(current));
            loading = false;
            return;
        }
        try {
            show(files.get(index), ExamFile.load(files.get(index)));
        } catch (IOException | IllegalArgumentException e) {
            error("Không mở được " + files.get(index).getFileName() + ":\n" + e.getMessage());
        }
    }

    private void show(Path file, JsonObject exam) {
        loading = true;
        current = file;
        titleField.setText(exam.get("title").getAsString());
        minutes.setValue(exam.get("durationMinutes").getAsInt());
        questions.clear();
        questions.addAll(ExamBank.questionsOf(exam));
        tableModel.fireTableDataChanged();
        loading = false;
        dirty = false;
    }

    private boolean confirmDiscard() {
        return !dirty || JOptionPane.showConfirmDialog(this, "Đề đang soạn chưa lưu. Bỏ các thay đổi?", "Xác nhận",
            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private void newExam() {
        if (!confirmDiscard()) { return; }
        loading = true;
        fileList.clearSelection();
        loading = false;
        JsonObject empty = ExamBank.build("Đề mới", 15, List.of());
        show(null, empty);
        dirty = true;
    }

    private void duplicate() {
        int index = fileList.getSelectedIndex();
        if (index < 0 || !confirmDiscard()) { return; }
        try {
            JsonObject exam = ExamFile.load(files.get(index));
            exam.addProperty("title", exam.get("title").getAsString() + " (bản sao)");
            Path copy = freeName(exam.get("title").getAsString());
            ExamBank.save(copy, exam);
            refreshFiles(copy);
            show(copy, exam);
        } catch (IOException | IllegalArgumentException e) {
            error(e.getMessage());
        }
    }

    private void delete() {
        int index = fileList.getSelectedIndex();
        if (index < 0) { return; }
        Path file = files.get(index);
        if (JOptionPane.showConfirmDialog(this, "Xoá file " + file.getFileName() + "?", "Xác nhận",
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) { return; }
        try {
            Files.delete(file);
        } catch (IOException e) {
            error("Không xoá được: " + e.getMessage());
            return;
        }
        if (file.equals(current)) { current = null; dirty = false; }
        refreshFiles(null);
    }

    /** Tên file chưa dùng, dựa trên tiêu đề (thêm -2, -3... nếu trùng). */
    private Path freeName(String title) {
        String name = ExamBank.fileName(title);
        Path path = dir.resolve(name);
        for (int n = 2; Files.exists(path); n++) {
            path = dir.resolve(name.replace(".json", "-" + n + ".json"));
        }
        return path;
    }

    private void save() {
        if (room.isStarted()) { error("Bài thi đã bắt đầu, không sửa đề được"); return; }
        String title = titleField.getText().trim();
        if (title.isEmpty()) { error("Hãy nhập tên đề"); return; }
        try {
            Files.createDirectories(dir);
            Path target = current != null ? current : freeName(title);
            ExamBank.save(target, ExamBank.build(title, (Integer) minutes.getValue(), questions));
            current = target;
            dirty = false;
            refreshFiles(target);
        } catch (IOException | IllegalArgumentException e) {
            error("Không lưu được: " + e.getMessage());
        }
    }

    private void useExam() {
        if (current == null || dirty) { error("Hãy lưu đề trước khi dùng"); return; }
        try {
            room.loadExam(current);
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            error(e.getMessage());
        }
    }

    private void close() {
        if (confirmDiscard()) { dispose(); }
    }

    // ---------- Câu hỏi ----------

    private void addQuestion() {
        ExamBank.Question q = QuestionDialog.edit(this, null);
        if (q != null) { questions.add(q); changed(questions.size() - 1); }
    }

    private void editQuestion() {
        int row = table.getSelectedRow();
        if (row < 0) { return; }
        ExamBank.Question q = QuestionDialog.edit(this, questions.get(row));
        if (q != null) { questions.set(row, q); changed(row); }
    }

    private void removeQuestion() {
        int row = table.getSelectedRow();
        if (row < 0) { return; }
        questions.remove(row);
        changed(Math.min(row, questions.size() - 1));
    }

    private void move(int delta) {
        int row = table.getSelectedRow(), to = row + delta;
        if (row < 0 || to < 0 || to >= questions.size()) { return; }
        Collections.swap(questions, row, to);
        changed(to);
    }

    private void changed(int selectRow) {
        dirty = true;
        tableModel.fireTableDataChanged();
        if (selectRow >= 0) { table.setRowSelectionInterval(selectRow, selectRow); }
    }

    private void importCsv() {
        JFileChooser chooser = new JFileChooser(".");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) { return; }
        try {
            List<ExamBank.Question> added = ExamBank.loadCsv(chooser.getSelectedFile().toPath());
            questions.addAll(added);
            changed(questions.size() - 1);
            JOptionPane.showMessageDialog(this, "Đã thêm " + added.size() + " câu (bấm Lưu để ghi vào file)");
        } catch (IOException | IllegalArgumentException e) {
            error("File CSV có lỗi:\n" + e.getMessage());
        }
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(this, message, "Quản lý đề", JOptionPane.ERROR_MESSAGE);
    }

    private class QuestionTable extends AbstractTableModel {
        private static final String[] COLUMNS = {"#", "Câu hỏi", "Đáp án đúng"};

        @Override public int getRowCount() { return questions.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int c) { return COLUMNS[c]; }

        @Override
        public Object getValueAt(int row, int col) {
            ExamBank.Question q = questions.get(row);
            return switch (col) {
                case 0 -> row + 1;
                case 1 -> q.text();
                default -> (char) ('A' + q.answer()) + ". " + q.options().get(q.answer());
            };
        }
    }
}
