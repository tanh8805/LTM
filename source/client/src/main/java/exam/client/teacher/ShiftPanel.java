// Owner: Nguoi2

package exam.client.teacher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.client.TeacherClient;
import exam.common.model.MonitoringRules;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

/**
 * Tab "Ca thi": tạo ca thi (đề, mã ca, thời lượng, giờ hẹn, danh sách thí sinh, luật giám sát),
 * bắt đầu hoặc kết thúc ca, xem danh sách thí sinh.
 */
public class ShiftPanel extends JPanel {

    private final TeacherClient client;
    private final DefaultTableModel shiftModel = new DefaultTableModel(
            new Object[] {"Mã ca", "Đề", "Trạng thái", "Thời lượng (giây)"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable shiftTable = new JTable(shiftModel);

    // --- form tạo ca ---
    private final JComboBox<String> examBox = new JComboBox<>();
    private final List<Integer> examIds = new ArrayList<>();
    private final JTextField codeField = new JTextField(10);
    private final JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 600, 5));
    private final JTextField startTimeField = new JTextField(16);
    private final JTextArea candidatesArea = new JTextArea(5, 20);
    private final JSpinner focusSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 100, 1));
    private final JTextField denylistField = new JTextField(30);
    private final JTextField allowlistField = new JTextField(30);
    private final JTextField domainsField = new JTextField(30);

    public ShiftPanel(TeacherClient client) {
        super(new BorderLayout(8, 8));
        this.client = client;
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        MonitoringRules defaults = MonitoringRules.createDefault();
        denylistField.setText(String.join(", ", defaults.processDenylist));
        domainsField.setText(String.join(", ", defaults.blockedDomains));
        focusSpinner.setValue(defaults.focusLossThreshold);
        candidatesArea.setText("SV001\nSV002\nSV003\nSV004\nSV005");

        JButton refreshButton = new JButton("Làm mới");
        JButton startButton = new JButton("Bắt đầu ca đang chọn");
        JButton endButton = new JButton("Kết thúc ca đang chọn");
        JButton candidatesButton = new JButton("Xem thí sinh");
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(refreshButton);
        top.add(startButton);
        top.add(endButton);
        top.add(candidatesButton);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(shiftTable), BorderLayout.CENTER);
        add(buildCreateForm(), BorderLayout.SOUTH);

        refreshButton.addActionListener(event -> refresh());
        startButton.addActionListener(event -> callOnSelectedShift("START_SHIFT"));
        endButton.addActionListener(event -> callOnSelectedShift("END_SHIFT"));
        candidatesButton.addActionListener(event -> showCandidatesOfSelectedShift());
    }

    private JPanel buildCreateForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Tạo ca thi mới"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("Đề thi:"), c);
        c.gridx = 1;
        form.add(examBox, c);
        c.gridx = 2;
        form.add(new JLabel("Mã ca (để trống = tự sinh):"), c);
        c.gridx = 3;
        form.add(codeField, c);

        c.gridx = 0;
        c.gridy = 1;
        form.add(new JLabel("Thời lượng (phút):"), c);
        c.gridx = 1;
        form.add(durationSpinner, c);
        c.gridx = 2;
        form.add(new JLabel("Giờ hẹn (yyyy-MM-dd HH:mm, trống = bắt đầu thủ công):"), c);
        c.gridx = 3;
        form.add(startTimeField, c);

        c.gridx = 0;
        c.gridy = 2;
        form.add(new JLabel("Thí sinh (mỗi dòng một mã SV):"), c);
        c.gridx = 1;
        form.add(new JScrollPane(candidatesArea), c);
        JButton loadCsvButton = new JButton("Mở file CSV...");
        c.gridx = 2;
        form.add(loadCsvButton, c);

        c.gridx = 0;
        c.gridy = 3;
        form.add(new JLabel("Luật: ngưỡng mất focus:"), c);
        c.gridx = 1;
        form.add(focusSpinner, c);
        c.gridx = 2;
        form.add(new JLabel("Process cấm (phẩy):"), c);
        c.gridx = 3;
        form.add(denylistField, c);

        c.gridx = 0;
        c.gridy = 4;
        form.add(new JLabel("Process cho phép thêm:"), c);
        c.gridx = 1;
        form.add(allowlistField, c);
        c.gridx = 2;
        form.add(new JLabel("Domain bị chặn (phẩy):"), c);
        c.gridx = 3;
        form.add(domainsField, c);

        JButton createButton = new JButton("Tạo ca thi");
        c.gridx = 3;
        c.gridy = 5;
        form.add(createButton, c);

        loadCsvButton.addActionListener(event -> loadCandidatesFromFile());
        createButton.addActionListener(event -> createShift());
        return form;
    }

    public void refresh() {
        TeacherUi.call(client, this, "LIST_SHIFTS", new JsonObject(), data -> {
            shiftModel.setRowCount(0);
            for (JsonElement element : data.getAsJsonArray("shifts")) {
                JsonObject shift = element.getAsJsonObject();
                shiftModel.addRow(new Object[] {shift.get("code").getAsString(), shift.get("examTitle").getAsString(),
                        shift.get("status").getAsString(), shift.get("durationSeconds").getAsInt()});
            }
        });
        TeacherUi.call(client, this, "LIST_EXAMS", new JsonObject(), data -> {
            examBox.removeAllItems();
            examIds.clear();
            for (JsonElement element : data.getAsJsonArray("exams")) {
                JsonObject exam = element.getAsJsonObject();
                examIds.add(exam.get("id").getAsInt());
                examBox.addItem(exam.get("id").getAsInt() + " - " + exam.get("title").getAsString());
            }
        });
    }

    private void createShift() {
        if (examBox.getSelectedIndex() < 0) {
            TeacherUi.showInfo(this, "Chưa có đề thi nào. Hãy tạo đề ở tab \"Đề thi\" rồi bấm Làm mới.");
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("examId", examIds.get(examBox.getSelectedIndex()));
        request.addProperty("code", codeField.getText().trim());
        request.addProperty("durationMinutes", (Integer) durationSpinner.getValue());
        request.addProperty("candidatesCsv", candidatesArea.getText());

        String startText = startTimeField.getText().trim();
        if (!startText.isEmpty()) {
            try {
                LocalDateTime startTime = LocalDateTime.parse(startText.replace(' ', 'T'));
                request.addProperty("startTime", startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            } catch (DateTimeParseException e) {
                TeacherUi.showError(this, "Giờ hẹn phải có dạng yyyy-MM-dd HH:mm, ví dụ 2026-10-30 08:00");
                return;
            }
        }

        JsonObject rules = new JsonObject();
        rules.addProperty("focusLossThreshold", (Integer) focusSpinner.getValue());
        rules.add("processDenylist", splitList(denylistField.getText()));
        rules.add("processAllowlist", splitList(allowlistField.getText()));
        rules.add("blockedDomains", splitList(domainsField.getText()));
        request.add("rules", rules);

        TeacherUi.call(client, this, "CREATE_SHIFT", request, data -> {
            StringBuilder message = new StringBuilder("Đã tạo ca thi " + data.get("code").getAsString()
                    + " với " + data.get("candidatesAdded").getAsInt() + " thí sinh.");
            for (JsonElement error : data.getAsJsonArray("errors")) {
                message.append("\n - ").append(error.getAsString());
            }
            TeacherUi.showInfo(this, message.toString());
            refresh();
        });
    }

    private JsonArray splitList(String text) {
        JsonArray array = new JsonArray();
        for (String part : text.split(",")) {
            if (!part.isBlank()) {
                array.add(part.trim());
            }
        }
        return array;
    }

    private void loadCandidatesFromFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            candidatesArea.setText(Files.readString(chooser.getSelectedFile().toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            TeacherUi.showError(this, "Không đọc được file (cần UTF-8): " + e.getMessage());
        }
    }

    private String selectedShiftCode() {
        int row = shiftTable.getSelectedRow();
        if (row < 0) {
            TeacherUi.showInfo(this, "Hãy chọn một ca thi trong bảng.");
            return null;
        }
        return (String) shiftModel.getValueAt(row, 0);
    }

    private void callOnSelectedShift(String action) {
        String code = selectedShiftCode();
        if (code == null) {
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("code", code);
        TeacherUi.call(client, this, action, request, data -> refresh());
    }

    private void showCandidatesOfSelectedShift() {
        String code = selectedShiftCode();
        if (code == null) {
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("code", code);
        TeacherUi.call(client, this, "GET_SHIFT", request, data -> {
            StringBuilder text = new StringBuilder("Ca " + code + " (" + data.get("status").getAsString() + ")\n");
            for (JsonElement element : data.getAsJsonArray("candidates")) {
                JsonObject candidate = element.getAsJsonObject();
                text.append(candidate.get("studentCode").getAsString()).append("  ")
                        .append(candidate.get("fullName").getAsString()).append("\n");
            }
            TeacherUi.showInfo(this, text.toString());
        });
    }
}
