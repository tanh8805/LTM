// Owner: Nguoi2

package exam.client.teacher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.client.TeacherClient;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/** Tab "Câu hỏi": xem, thêm, sửa, xóa câu hỏi và nhập từ CSV. */
public class QuestionPanel extends JPanel {

    private static final String[] LETTERS = {"A", "B", "C", "D"};

    private final TeacherClient client;
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[] {"ID", "Câu hỏi", "A", "B", "C", "D", "Đúng"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);

    public QuestionPanel(TeacherClient client) {
        super(new BorderLayout());
        this.client = client;

        JButton refreshButton = new JButton("Làm mới");
        JButton addButton = new JButton("Thêm");
        JButton editButton = new JButton("Sửa");
        JButton deleteButton = new JButton("Xóa");
        JButton importButton = new JButton("Nhập CSV...");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(refreshButton);
        buttons.add(addButton);
        buttons.add(editButton);
        buttons.add(deleteButton);
        buttons.add(importButton);

        add(buttons, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(new JLabel("  CSV: câu hỏi, A, B, C, D, đáp án đúng (A-D hoặc 1-4); dòng đầu có thể là tiêu đề; lưu UTF-8"),
                BorderLayout.SOUTH);

        refreshButton.addActionListener(event -> refresh());
        addButton.addActionListener(event -> addQuestion());
        editButton.addActionListener(event -> editSelectedQuestion());
        deleteButton.addActionListener(event -> deleteSelectedQuestion());
        importButton.addActionListener(event -> importCsv());
    }

    /** Tải lại danh sách câu hỏi từ Server. */
    public void refresh() {
        TeacherUi.call(client, this, "LIST_QUESTIONS", new JsonObject(), data -> {
            tableModel.setRowCount(0);
            for (JsonElement element : data.getAsJsonArray("questions")) {
                JsonObject question = element.getAsJsonObject();
                JsonArray options = question.getAsJsonArray("options");
                tableModel.addRow(new Object[] {
                        question.get("id").getAsInt(), question.get("content").getAsString(),
                        options.get(0).getAsString(), options.get(1).getAsString(),
                        options.get(2).getAsString(), options.get(3).getAsString(),
                        LETTERS[question.get("correctIndex").getAsInt()]});
            }
        });
    }

    private void addQuestion() {
        JsonObject form = showQuestionDialog("Thêm câu hỏi", null);
        if (form != null) {
            TeacherUi.call(client, this, "ADD_QUESTION", form, data -> refresh());
        }
    }

    private void editSelectedQuestion() {
        int row = table.getSelectedRow();
        if (row < 0) {
            TeacherUi.showInfo(this, "Hãy chọn một câu hỏi trong bảng.");
            return;
        }
        JsonObject current = new JsonObject();
        current.addProperty("id", (Integer) tableModel.getValueAt(row, 0));
        current.addProperty("content", (String) tableModel.getValueAt(row, 1));
        JsonArray options = new JsonArray();
        for (int column = 2; column <= 5; column++) {
            options.add((String) tableModel.getValueAt(row, column));
        }
        current.add("options", options);
        current.addProperty("correctIndex", indexOfLetter((String) tableModel.getValueAt(row, 6)));

        JsonObject form = showQuestionDialog("Sửa câu hỏi", current);
        if (form != null) {
            form.addProperty("id", current.get("id").getAsInt());
            TeacherUi.call(client, this, "UPDATE_QUESTION", form, data -> refresh());
        }
    }

    private void deleteSelectedQuestion() {
        int row = table.getSelectedRow();
        if (row < 0) {
            TeacherUi.showInfo(this, "Hãy chọn một câu hỏi trong bảng.");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Xóa câu hỏi đang chọn?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("id", (Integer) tableModel.getValueAt(row, 0));
        TeacherUi.call(client, this, "DELETE_QUESTION", request, data -> refresh());
    }

    private void importCsv() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();
        String csvText;
        try {
            csvText = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // MalformedInputException (không phải UTF-8) cũng là IOException.
            TeacherUi.showError(this, "Không đọc được file (cần UTF-8): " + e.getMessage());
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("csv", csvText);
        TeacherUi.call(client, this, "IMPORT_QUESTIONS_CSV", request, data -> {
            StringBuilder summary = new StringBuilder();
            summary.append("Đã thêm: ").append(data.get("added").getAsInt())
                    .append("\nBỏ qua: ").append(data.get("skipped").getAsInt());
            for (JsonElement error : data.getAsJsonArray("errors")) {
                summary.append("\n - ").append(error.getAsString());
            }
            TeacherUi.showInfo(this, summary.toString());
            refresh();
        });
    }

    /** Hộp thoại nhập câu hỏi. Trả về null nếu người dùng bấm Hủy. current = null khi thêm mới. */
    private JsonObject showQuestionDialog(String title, JsonObject current) {
        JTextArea contentArea = new JTextArea(3, 40);
        contentArea.setLineWrap(true);
        JTextField[] optionFields = new JTextField[4];
        JComboBox<String> correctBox = new JComboBox<>(LETTERS);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("Câu hỏi:"), c);
        c.gridx = 1;
        form.add(new JScrollPane(contentArea), c);
        for (int i = 0; i < 4; i++) {
            optionFields[i] = new JTextField(40);
            c.gridx = 0;
            c.gridy = i + 1;
            form.add(new JLabel("Đáp án " + LETTERS[i] + ":"), c);
            c.gridx = 1;
            form.add(optionFields[i], c);
        }
        c.gridx = 0;
        c.gridy = 5;
        form.add(new JLabel("Đáp án đúng:"), c);
        c.gridx = 1;
        form.add(correctBox, c);

        if (current != null) {
            contentArea.setText(current.get("content").getAsString());
            for (int i = 0; i < 4; i++) {
                optionFields[i].setText(current.getAsJsonArray("options").get(i).getAsString());
            }
            correctBox.setSelectedIndex(current.get("correctIndex").getAsInt());
        }

        if (JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.addProperty("content", contentArea.getText());
        JsonArray options = new JsonArray();
        for (JTextField field : optionFields) {
            options.add(field.getText());
        }
        result.add("options", options);
        result.addProperty("correctIndex", correctBox.getSelectedIndex());
        return result;
    }

    private int indexOfLetter(String letter) {
        for (int i = 0; i < LETTERS.length; i++) {
            if (LETTERS[i].equals(letter)) {
                return i;
            }
        }
        return 0;
    }
}
