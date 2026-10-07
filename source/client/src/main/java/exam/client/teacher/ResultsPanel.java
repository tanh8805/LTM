// Owner: Nguoi2

package exam.client.teacher;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.client.TeacherClient;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** Tab "Điểm": xem điểm từng ca thi và xuất CSV. */
public class ResultsPanel extends JPanel {

    private final TeacherClient client;
    private final JComboBox<String> shiftBox = new JComboBox<>();
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[] {"Mã SV", "Họ tên", "Trạng thái", "Điểm (thang 10)", "Đúng", "Tổng câu", "Đã trả lời"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    public ResultsPanel(TeacherClient client) {
        super(new BorderLayout());
        this.client = client;

        JButton refreshShiftsButton = new JButton("Làm mới danh sách ca");
        JButton showButton = new JButton("Xem điểm");
        JButton exportButton = new JButton("Xuất CSV...");
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Ca thi:"));
        top.add(shiftBox);
        top.add(refreshShiftsButton);
        top.add(showButton);
        top.add(exportButton);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(new JTable(tableModel)), BorderLayout.CENTER);

        refreshShiftsButton.addActionListener(event -> refreshShifts());
        showButton.addActionListener(event -> showResults());
        exportButton.addActionListener(event -> exportCsv());
    }

    public void refreshShifts() {
        TeacherUi.call(client, this, "LIST_SHIFTS", new JsonObject(), data -> {
            shiftBox.removeAllItems();
            List<String> codes = new ArrayList<>();
            for (JsonElement element : data.getAsJsonArray("shifts")) {
                codes.add(element.getAsJsonObject().get("code").getAsString());
            }
            for (String code : codes) {
                shiftBox.addItem(code);
            }
        });
    }

    private void showResults() {
        String code = (String) shiftBox.getSelectedItem();
        if (code == null) {
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("code", code);
        TeacherUi.call(client, this, "LIST_RESULTS", request, data -> {
            tableModel.setRowCount(0);
            for (JsonElement element : data.getAsJsonArray("rows")) {
                JsonObject row = element.getAsJsonObject();
                boolean submitted = row.get("submitted").getAsBoolean();
                String status = !submitted ? "Chưa nộp" : (row.get("autoSubmitted").getAsBoolean() ? "Server tự chốt" : "Đã nộp");
                tableModel.addRow(new Object[] {row.get("studentCode").getAsString(), row.get("fullName").getAsString(), status,
                        submitted ? row.get("score").getAsDouble() : "", submitted ? row.get("correctCount").getAsInt() : "",
                        submitted ? row.get("totalQuestions").getAsInt() : "", submitted ? row.get("answeredCount").getAsInt() : ""});
            }
        });
    }

    private void exportCsv() {
        String code = (String) shiftBox.getSelectedItem();
        if (code == null) {
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("code", code);
        TeacherUi.call(client, this, "EXPORT_RESULTS_CSV", request, data -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new java.io.File("diem-" + code + ".csv"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            try {
                // BOM ở đầu file để Excel nhận đúng UTF-8 (tên có dấu).
                Files.writeString(chooser.getSelectedFile().toPath(), "﻿" + data.get("csv").getAsString(), StandardCharsets.UTF_8);
                TeacherUi.showInfo(this, "Đã xuất " + chooser.getSelectedFile());
            } catch (IOException e) {
                TeacherUi.showError(this, "Không ghi được file: " + e.getMessage());
            }
        });
    }
}
