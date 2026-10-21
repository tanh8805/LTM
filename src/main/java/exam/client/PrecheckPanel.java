// Màn PRECHECK (danh sách việc cần làm kèm nút xử lý và dòng đã đạt, tự kiểm tra lại) và màn chờ giám thị bắt đầu (Người 1).
package exam.client;

import exam.common.Card;
import exam.common.Icons;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.Timer;

class PrecheckPanel extends JPanel {
    private static final int AUTO_SECONDS = 5;
    private static final String APP_PREFIXES[] = {"Đang mở ứng dụng bị cấm: ", "Hãy đóng ứng dụng: "}, USB_PREFIX = "Đang cắm USB: ";

    /** Một dòng của danh sách: ok = đã đạt; action là nhãn nút xử lý (rỗng nếu không có). */
    record Item(boolean ok, String title, String hint, String action) {}

    private final JPanel list = new JPanel();
    private final JLabel subtitle = Theme.label(" ", Theme.BASE_SIZE, false, Theme.MUTED);
    private final JLabel countdown = Theme.label(" ", 14, false, Theme.MUTED);
    private final JButton again = Theme.primaryButton("Kiểm tra lại");
    private final Runnable onRecheck;
    private final Timer auto = new Timer(1000, e -> tick());
    private int left = AUTO_SECONDS;

    PrecheckPanel(Runnable onRecheck) {
        super(new GridBagLayout());
        this.onRecheck = onRecheck;
        setBackground(Theme.BACKGROUND);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        again.addActionListener(e -> recheck());
        Card card = new Card();
        card.setLayout(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        card.add(list, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(countdown, BorderLayout.WEST);
        south.add(again, BorderLayout.EAST);
        JPanel box = new JPanel(new BorderLayout(0, 18));
        box.setOpaque(false);
        box.setPreferredSize(new Dimension(840, 520));
        JPanel head = new JPanel(new BorderLayout(0, 6));
        head.setOpaque(false);
        head.add(Theme.label("Kiểm tra máy trước giờ thi", 26, true, Theme.TEXT), BorderLayout.NORTH);
        head.add(subtitle, BorderLayout.SOUTH);
        box.add(head, BorderLayout.NORTH);
        JPanel middle = new JPanel(new BorderLayout());
        middle.setOpaque(false);
        middle.add(card, BorderLayout.NORTH);
        box.add(middle, BorderLayout.CENTER);
        box.add(south, BorderLayout.SOUTH);
        add(box, new GridBagConstraints());
    }

    private void recheck() {
        auto.stop();
        again.setEnabled(false);
        again.setText("Đang kiểm tra...");
        countdown.setText(" ");
        onRecheck.run();
    }

    private void tick() {
        left--;
        if (left <= 0) { recheck(); } else { countdown.setText(countdownText(left)); }
    }

    /** Chỉ chạy bộ đếm tự kiểm tra lại khi màn này đang hiện. */
    void setActive(boolean active) {
        if (!active) { auto.stop(); }
    }

    void setIssues(List<String> issues, String server) {
        again.setEnabled(true);
        again.setText("Kiểm tra lại");
        subtitle.setText(subtitle(issues.size()));
        list.removeAll();
        List<Item> items = items(issues, server);
        for (int i = 0; i < items.size(); i++) {
            list.add(row(items.get(i), i > 0));
        }
        list.revalidate();
        list.repaint();
        left = AUTO_SECONDS;
        countdown.setText(countdownText(left));
        auto.restart();
    }

    static String subtitle(int issues) { return "Còn " + issues + " việc cần làm. Danh sách tự cập nhật khi bạn xử lý xong."; }

    static String countdownText(int seconds) { return "Tự kiểm tra lại sau " + seconds + " giây"; }

    /** Các vấn đề trước (có hướng dẫn và nút xử lý), rồi các mục đã đạt. */
    static List<Item> items(List<String> issues, String server) {
        List<Item> items = new ArrayList<>();
        boolean appIssue = false, usbIssue = false;
        for (String issue : issues) {
            boolean app = false;
            for (String prefix : APP_PREFIXES) { app |= issue.startsWith(prefix); }
            boolean usb = issue.startsWith(USB_PREFIX);
            appIssue |= app;
            usbIssue |= usb;
            items.add(new Item(false, issue, hint(issue), app ? "Mở Quản lý tác vụ" : usb ? "Mở This PC" : ""));
        }
        if (!appIssue) { items.add(new Item(true, "Không có ứng dụng bị cấm đang mở", "", "")); }
        if (!usbIssue) { items.add(new Item(true, "Không có USB đang cắm", "", "")); }
        items.add(new Item(true, "Đã kết nối máy giám thị " + server, "", ""));
        return items;
    }

    /** Hướng dẫn xử lý cho từng loại vấn đề mà Monitor.precheck có thể báo. */
    static String hint(String issue) {
        for (String prefix : APP_PREFIXES) {
            if (issue.startsWith(prefix)) { return "Đóng ứng dụng " + issue.substring(prefix.length()); }
        }
        if (issue.startsWith(USB_PREFIX)) { return "Rút USB ở ổ " + issue.substring(USB_PREFIX.length()); }
        return "Hãy xử lý vấn đề này";
    }

    /** Mở công cụ giúp xử lý (Windows): lỗi thì bỏ qua, sinh viên vẫn làm tay được. */
    private static void launch(String action) {
        try {
            if (action.equals("Mở This PC")) { new ProcessBuilder("explorer.exe", "shell:MyComputerFolder").start(); }
            else { new ProcessBuilder("taskmgr.exe").start(); }
        } catch (IOException e) {
            System.err.println("Không mở được công cụ: " + e.getMessage());
        }
    }

    private static JComponent row(Item item, boolean separator) {
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
            separator ? BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.SEPARATOR) : BorderFactory.createEmptyBorder(),
            BorderFactory.createEmptyBorder(14, 22, 14, 22)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        row.add(new JLabel(item.ok() ? Icons.check(24) : Icons.cross(24)), BorderLayout.WEST);
        JPanel text = new JPanel(new GridBagLayout());
        text.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.weightx = 1; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(0, 0, 2, 0);
        text.add(Theme.label(item.title(), Theme.BASE_SIZE, item.ok() ? false : true, Theme.TEXT), c);
        if (!item.hint().isEmpty()) { text.add(Theme.label(item.hint(), 14, false, Theme.MUTED), c); }
        row.add(text, BorderLayout.CENTER);
        if (!item.action().isEmpty()) {
            JButton b = new JButton(item.action());
            b.setFocusable(false);
            b.addActionListener(e -> launch(item.action()));
            row.add(b, BorderLayout.EAST);
        }
        return row;
    }

    /** Màn chờ: máy đã đạt PRECHECK (dấu tích xanh), thanh tiến trình chạy liên tục. */
    static JPanel waitingPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Theme.BACKGROUND);
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JLabel check = new JLabel(Icons.bigCheck(72), SwingConstants.CENTER);
        JLabel text = Theme.label("Đã sẵn sàng. Chờ giám thị bắt đầu...", 28, true, Theme.TEXT);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setMaximumSize(new Dimension(420, 8));
        bar.setPreferredSize(new Dimension(420, 8));
        for (JComponent c : new JComponent[] {check, text, bar}) { c.setAlignmentX(CENTER_ALIGNMENT); }
        box.add(check);
        box.add(Box.createVerticalStrut(18));
        box.add(text);
        box.add(Box.createVerticalStrut(24));
        box.add(bar);
        p.add(box, new GridBagConstraints());
        return p;
    }
}
