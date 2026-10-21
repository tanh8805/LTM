package exam.server;

import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/** Hộp thoại sửa luật: ba tab chính và tab Nâng cao; Lưu thì ghi config/rules.json và gửi RULES_UPDATE. */
class RulesDialog extends JDialog {
    private final ExamServer server;
    private final JsonObject rules;
    private final ListEditor whitelist, blacklist, domains, allowedWindow, ignore;

    RulesDialog(JFrame owner, ExamServer server) {
        super(owner, "Quản lý ứng dụng", true);
        this.server = server;
        this.rules = server.room().rules();
        whitelist = new ListEditor(RulesFile.listOf(rules, RulesFile.WHITELIST), false);
        blacklist = new ListEditor(RulesFile.listOf(rules, RulesFile.BLACKLIST), false);
        domains = new ListEditor(RulesFile.listOf(rules, RulesFile.DOMAINS), true);
        allowedWindow = new ListEditor(RulesFile.listOf(rules, RulesFile.ALLOWED_WINDOW), false);
        ignore = new ListEditor(RulesFile.listOf(rules, RulesFile.IGNORE), false);

        JPanel advanced = new JPanel(new BorderLayout());
        JLabel note = new JLabel("Chỉ sửa khi biết rõ: sai có thể khiến máy sinh viên bị báo vi phạm oan hoặc bỏ sót.");
        note.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        JPanel lists = new JPanel(new java.awt.GridLayout(2, 1));
        lists.add(titled("allowedWindowApps (được phép có cửa sổ)", allowedWindow));
        lists.add(titled("ignoreProcesses (bỏ qua hoàn toàn)", ignore));
        advanced.add(note, BorderLayout.NORTH);
        advanced.add(lists, BorderLayout.CENTER);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Được phép", whitelist);
        tabs.addTab("Bị cấm", blacklist);
        tabs.addTab("Tên miền bị cấm", domains);
        tabs.addTab("Nâng cao", advanced);

        JButton save = new JButton("Lưu"), cancel = new JButton("Huỷ");
        save.addActionListener(e -> save());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(cancel);
        buttons.add(save);
        add(tabs, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setSize(640, 520);
        setLocationRelativeTo(owner);
    }

    private static JPanel titled(String title, ListEditor editor) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(editor, BorderLayout.CENTER);
        return p;
    }

    private void save() {
        JsonObject updated = RulesFile.withList(rules, RulesFile.WHITELIST, whitelist.items());
        updated = RulesFile.withList(updated, RulesFile.BLACKLIST, blacklist.items());
        updated = RulesFile.withList(updated, RulesFile.DOMAINS, domains.items());
        updated = RulesFile.withList(updated, RulesFile.ALLOWED_WINDOW, allowedWindow.items());
        updated = RulesFile.withList(updated, RulesFile.IGNORE, ignore.items());
        try {
            server.applyRules(updated);
            dispose();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Không ghi được rules.json", JOptionPane.ERROR_MESSAGE);
        }
    }
}
