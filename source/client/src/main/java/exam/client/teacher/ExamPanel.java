// Owner: Nguoi2

package exam.client.teacher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.client.TeacherClient;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

/** Tab "Đề thi": danh sách đề, tạo đề từ các câu chọn tay hoặc chọn ngẫu nhiên N câu. */
public class ExamPanel extends JPanel {

    private final TeacherClient client;
    private final DefaultTableModel examModel = new DefaultTableModel(
            new Object[] {"ID", "Tên đề", "Phút", "Số câu"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final DefaultTableModel questionModel = new DefaultTableModel(new Object[] {"ID", "Câu hỏi"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable examTable = new JTable(examModel);
    private final JTable questionTable = new JTable(questionModel);
    private final JTextField titleField = new JTextField("Đề thi mới", 20);
    private final JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 600, 5));
    private final JSpinner randomCountSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 1000, 1));

    public ExamPanel(TeacherClient client) {
        super(new BorderLayout(8, 8));
        this.client = client;
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Bên trái: các đề đã có. Bên phải: chọn câu hỏi để tạo đề mới.
        JPanel left = new JPanel(new BorderLayout());
        left.add(new JLabel("Các đề thi"), BorderLayout.NORTH);
        left.add(new JScrollPane(examTable), BorderLayout.CENTER);

        questionTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JPanel right = new JPanel(new BorderLayout());
        right.add(new JLabel("Chọn câu hỏi (giữ Ctrl để chọn nhiều) hoặc để máy chọn ngẫu nhiên"), BorderLayout.NORTH);
        right.add(new JScrollPane(questionTable), BorderLayout.CENTER);

        JPanel form = new JPanel(new GridLayout(0, 1, 2, 2));
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        titleRow.add(new JLabel("Tên đề:"));
        titleRow.add(titleField);
        titleRow.add(new JLabel("Thời gian (phút):"));
        titleRow.add(durationSpinner);
        JButton manualButton = new JButton("Tạo đề từ các câu đã chọn");
        JButton randomButton = new JButton("Tạo đề ngẫu nhiên");
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionRow.add(manualButton);
        actionRow.add(new JLabel("hoặc ngẫu nhiên"));
        actionRow.add(randomCountSpinner);
        actionRow.add(new JLabel("câu:"));
        actionRow.add(randomButton);
        form.add(titleRow);
        form.add(actionRow);
        right.add(form, BorderLayout.SOUTH);

        JButton refreshButton = new JButton("Làm mới");
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(refreshButton);

        JPanel center = new JPanel(new GridLayout(1, 2, 8, 8));
        center.add(left);
        center.add(right);
        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);

        refreshButton.addActionListener(event -> refresh());
        manualButton.addActionListener(event -> createManualExam());
        randomButton.addActionListener(event -> createRandomExam());
    }

    public void refresh() {
        TeacherUi.call(client, this, "LIST_EXAMS", new JsonObject(), data -> {
            examModel.setRowCount(0);
            for (JsonElement element : data.getAsJsonArray("exams")) {
                JsonObject exam = element.getAsJsonObject();
                examModel.addRow(new Object[] {exam.get("id").getAsInt(), exam.get("title").getAsString(),
                        exam.get("durationMinutes").getAsInt(), exam.get("questionCount").getAsInt()});
            }
        });
        TeacherUi.call(client, this, "LIST_QUESTIONS", new JsonObject(), data -> {
            questionModel.setRowCount(0);
            for (JsonElement element : data.getAsJsonArray("questions")) {
                JsonObject question = element.getAsJsonObject();
                questionModel.addRow(new Object[] {question.get("id").getAsInt(), question.get("content").getAsString()});
            }
        });
    }

    private void createManualExam() {
        int[] selectedRows = questionTable.getSelectedRows();
        if (selectedRows.length == 0) {
            TeacherUi.showInfo(this, "Hãy chọn ít nhất một câu hỏi trong bảng bên phải.");
            return;
        }
        JsonArray ids = new JsonArray();
        for (int row : selectedRows) {
            ids.add((Integer) questionModel.getValueAt(row, 0));
        }
        JsonObject request = baseExamRequest();
        request.add("questionIds", ids);
        TeacherUi.call(client, this, "CREATE_EXAM", request, data -> {
            TeacherUi.showInfo(this, "Đã tạo đề id=" + data.get("examId").getAsInt());
            refresh();
        });
    }

    private void createRandomExam() {
        JsonObject request = baseExamRequest();
        request.addProperty("randomCount", (Integer) randomCountSpinner.getValue());
        TeacherUi.call(client, this, "CREATE_EXAM", request, data -> {
            TeacherUi.showInfo(this, "Đã tạo đề id=" + data.get("examId").getAsInt()
                    + " với " + data.getAsJsonArray("questionIds").size() + " câu ngẫu nhiên");
            refresh();
        });
    }

    private JsonObject baseExamRequest() {
        JsonObject request = new JsonObject();
        request.addProperty("title", titleField.getText());
        request.addProperty("durationMinutes", (Integer) durationSpinner.getValue());
        return request;
    }
}
