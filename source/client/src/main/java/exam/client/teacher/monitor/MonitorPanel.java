// Owner: Nguoi3

package exam.client.teacher.monitor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.client.TeacherClient;
import exam.client.teacher.TeacherUi;
import exam.common.protocol.AlertMessage;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Tab "Giám sát" của giáo viên.
 *
 *   Bảng máy: Machine, Student, Online/Offline, Alert level (đỏ/vàng), Reason, Rate. Tự làm mới mỗi 2 giây
 *   và ngay khi nhận ALERT. Hàng ĐỎ nền đỏ nhạt, hàng VÀNG nền vàng nhạt, máy offline chữ xám.
 *   "Chi tiết": số liệu mới nhất, process, địa chỉ đích, vi phạm gần đây của máy đang chọn.
 *   NOTICE: gửi tới máy đang chọn hoặc cả phòng. BASELINE: bật/tắt thu số liệu nền (mỗi máy 1 giây, không ML).
 */
public class MonitorPanel extends JPanel {

    private static final int REFRESH_INTERVAL_MS = 2000;
    private static final int COLUMN_ONLINE = 2;
    private static final int COLUMN_LEVEL = 3;

    private final TeacherClient client;
    private final JComboBox<String> shiftBox = new JComboBox<>();
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[] {"Machine", "Student", "Online", "Alert level", "Reason", "Rate"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final JTextArea alertLog = new JTextArea(5, 40);
    private final JTextField noticeField = new JTextField(30);
    private final JToggleButton baselineButton = new JToggleButton("BASELINE: tắt");
    private final Timer refreshTimer;

    public MonitorPanel(TeacherClient client) {
        super(new BorderLayout(6, 6));
        this.client = client;

        JButton refreshShiftsButton = new JButton("Làm mới danh sách ca");
        JButton detailButton = new JButton("Chi tiết máy đang chọn");
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Ca thi:"));
        top.add(shiftBox);
        top.add(refreshShiftsButton);
        top.add(detailButton);
        top.add(baselineButton);

        table.setDefaultRenderer(Object.class, new LevelRenderer());
        alertLog.setEditable(false);
        JSplitPane center = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(alertLog));
        center.setResizeWeight(0.75);

        JButton noticeOneButton = new JButton("Gửi máy đang chọn");
        JButton noticeAllButton = new JButton("Gửi cả phòng");
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(new JLabel("NOTICE:"));
        bottom.add(noticeField);
        bottom.add(noticeOneButton);
        bottom.add(noticeAllButton);

        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        refreshShiftsButton.addActionListener(event -> refreshShifts());
        detailButton.addActionListener(event -> showDetail());
        noticeOneButton.addActionListener(event -> sendNoticeToSelectedMachine());
        noticeAllButton.addActionListener(event -> sendNotice("ALL"));
        baselineButton.addActionListener(event -> toggleBaseline());

        refreshTimer = new Timer(REFRESH_INTERVAL_MS, event -> refreshMachines());
    }

    /** Bắt đầu tự làm mới (gọi khi giáo viên đăng nhập xong). */
    public void start() {
        refreshShifts();
        refreshTimer.start();
    }

    private void refreshShifts() {
        TeacherUi.call(client, this, "LIST_SHIFTS", new JsonObject(), data -> {
            Object selected = shiftBox.getSelectedItem();
            shiftBox.removeAllItems();
            List<String> codes = new ArrayList<>();
            for (JsonElement element : data.getAsJsonArray("shifts")) {
                codes.add(element.getAsJsonObject().get("code").getAsString());
            }
            for (String code : codes) {
                shiftBox.addItem(code);
            }
            if (selected != null && codes.contains(selected)) {
                shiftBox.setSelectedItem(selected);
            }
        });
    }

    /** Tải lại bảng máy. Không hiện hộp thoại lỗi (chạy mỗi 2 giây), lỗi chỉ bỏ qua lần này. */
    private void refreshMachines() {
        String code = (String) shiftBox.getSelectedItem();
        if (code == null || !client.isLoggedIn()) {
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("code", code);
        TeacherUi.callRaw(client, "LIST_MACHINES", request, response -> {
            if (!response.ok) {
                return;
            }
            int selectedRow = table.getSelectedRow();
            tableModel.setRowCount(0);
            for (JsonElement element : response.data.getAsJsonArray("machines")) {
                JsonObject machine = element.getAsJsonObject();
                tableModel.addRow(new Object[] {machine.get("machineId").getAsString(), machine.get("fullName").getAsString(),
                        machine.get("online").getAsBoolean() ? "Online" : "Offline", machine.get("level").getAsString(),
                        machine.get("reason").getAsString(), machine.get("rateMode").getAsString()});
            }
            if (selectedRow >= 0 && selectedRow < tableModel.getRowCount()) {
                table.setRowSelectionInterval(selectedRow, selectedRow);
            }
        });
    }

    /** Được gọi từ thread đọc socket khi nhận ALERT. */
    public void showAlert(AlertMessage alert) {
        SwingUtilities.invokeLater(() -> {
            alertLog.append("[" + alert.level + "] " + alert.machineId + ": " + alert.reason + "\n");
            alertLog.setCaretPosition(alertLog.getDocument().getLength());
            refreshMachines();
        });
    }

    private void showDetail() {
        int row = table.getSelectedRow();
        if (row < 0) {
            TeacherUi.showInfo(this, "Hãy chọn một máy trong bảng.");
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("machineId", (String) tableModel.getValueAt(row, 0));
        TeacherUi.call(client, this, "MACHINE_DETAIL", request, data -> {
            StringBuilder text = new StringBuilder();
            text.append("Máy: ").append(data.get("machineId").getAsString())
                    .append(" - ").append(data.get("fullName").getAsString()).append("\n");
            text.append("Online: ").append(data.get("online").getAsBoolean())
                    .append("   Mức: ").append(data.get("level").getAsString())
                    .append("   Rate: ").append(data.get("rateMode").getAsString()).append("\n");
            text.append("Lý do: ").append(data.get("reason").getAsString()).append("\n");
            text.append("Isolation Forest score: ").append(data.get("ifScore").getAsDouble()).append("\n");
            if (data.has("metrics")) {
                text.append("Số liệu: ").append(data.getAsJsonObject("metrics")).append("\n");
            }
            text.append("Process: ").append(data.getAsJsonArray("processNames")).append("\n");
            text.append("Địa chỉ đích: ").append(data.getAsJsonArray("remoteAddresses")).append("\n");
            JsonArray violations = data.getAsJsonArray("violations");
            text.append("Vi phạm gần đây (").append(violations.size()).append("):\n");
            for (JsonElement element : violations) {
                JsonObject violation = element.getAsJsonObject();
                text.append(" - ").append(violation.get("violationType").getAsString())
                        .append(": ").append(violation.get("evidence").getAsString()).append("\n");
            }
            JTextArea area = new JTextArea(text.toString(), 20, 70);
            area.setEditable(false);
            area.setLineWrap(true);
            JOptionPane.showMessageDialog(this, new JScrollPane(area), "Chi tiết máy", JOptionPane.PLAIN_MESSAGE);
        });
    }

    private void sendNoticeToSelectedMachine() {
        int row = table.getSelectedRow();
        if (row < 0) {
            TeacherUi.showInfo(this, "Hãy chọn một máy trong bảng.");
            return;
        }
        sendNotice((String) tableModel.getValueAt(row, 0));
    }

    private void sendNotice(String target) {
        String text = noticeField.getText().trim();
        if (text.isEmpty()) {
            TeacherUi.showInfo(this, "Hãy nhập nội dung thông báo.");
            return;
        }
        if (!client.sendNotice(target, text)) {
            TeacherUi.showError(this, "Chưa kết nối tới Server");
        }
    }

    private void toggleBaseline() {
        boolean enable = baselineButton.isSelected();
        JsonObject request = new JsonObject();
        request.addProperty("enabled", enable);
        TeacherUi.call(client, this, "SET_BASELINE", request, data -> baselineButton.setText(enable ? "BASELINE: bật" : "BASELINE: tắt"));
    }

    /** Tô màu hàng theo mức cảnh báo. */
    private class LevelRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (isSelected) {
                return component;
            }
            String level = (String) tableModel.getValueAt(row, COLUMN_LEVEL);
            boolean online = "Online".equals(tableModel.getValueAt(row, COLUMN_ONLINE));
            if ("RED".equals(level)) {
                component.setBackground(new Color(255, 190, 190));
            } else if ("YELLOW".equals(level)) {
                component.setBackground(new Color(255, 245, 170));
            } else {
                component.setBackground(Color.WHITE);
            }
            component.setForeground(online ? Color.BLACK : Color.GRAY);
            return component;
        }
    }
}
