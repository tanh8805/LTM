// Màn PRECHECK (mỗi vấn đề một dòng kèm hướng dẫn) và màn chờ giám thị bắt đầu (Người 1).
package exam.client;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;

class PrecheckPanel extends JPanel {
    private final JPanel list = new JPanel();
    private final JButton again = Theme.primaryButton("Kiểm tra lại");

    PrecheckPanel(Runnable onRecheck) {
        super(new GridBagLayout());
        setBackground(Theme.BACKGROUND);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        again.addActionListener(e -> {
            again.setEnabled(false);
            again.setText("Đang kiểm tra...");
            onRecheck.run();
        });
        JPanel box = new JPanel(new BorderLayout(0, 16));
        box.setOpaque(false);
        box.setPreferredSize(new Dimension(720, 440));
        box.add(Theme.label("Máy chưa đạt yêu cầu", 24, true, Theme.TEXT), BorderLayout.NORTH);
        box.add(list, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(again, BorderLayout.WEST);
        box.add(south, BorderLayout.SOUTH);
        add(box, new GridBagConstraints());
    }

    void setIssues(List<String> issues) {
        again.setEnabled(true);
        again.setText("Kiểm tra lại");
        list.removeAll();
        JLabel intro = Theme.label("Hãy xử lý các vấn đề sau rồi bấm Kiểm tra lại:", Theme.BASE_SIZE, false, Theme.MUTED);
        intro.setAlignmentX(LEFT_ALIGNMENT);
        list.add(intro);
        list.add(Box.createVerticalStrut(12));
        for (String issue : issues) {
            list.add(row(issue));
            list.add(Box.createVerticalStrut(10));
        }
        list.revalidate();
        list.repaint();
    }

    /** Hướng dẫn xử lý cho từng loại vấn đề mà Monitor.precheck có thể báo. */
    static String hint(String issue) {
        for (String prefix : new String[] {"Đang mở ứng dụng bị cấm: ", "Hãy đóng ứng dụng: "}) {
            if (issue.startsWith(prefix)) { return "Đóng ứng dụng " + issue.substring(prefix.length()); }
        }
        if (issue.startsWith("Đang cắm USB: ")) { return "Rút USB ở ổ " + issue.substring("Đang cắm USB: ".length()); }
        return "Hãy xử lý vấn đề này";
    }

    private static JPanel row(String issue) {
        Theme.Card card = new Theme.Card();
        card.setLayout(new BorderLayout(14, 0));
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.add(new JLabel(Theme.warningIcon(30)), BorderLayout.WEST);
        JPanel text = new JPanel(new GridBagLayout());
        text.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.weightx = 1; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(0, 0, 2, 0);
        text.add(Theme.label(issue, Theme.BASE_SIZE, true, Theme.TEXT), c);
        text.add(Theme.label(hint(issue), 14, false, Theme.WARNING), c);
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    /** Màn chờ: máy đã đạt PRECHECK (dấu tích xanh), thanh tiến trình chạy liên tục. */
    static JPanel waitingPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Theme.BACKGROUND);
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JLabel check = new JLabel(Theme.checkIcon(72), SwingConstants.CENTER);
        JLabel text = Theme.label("Đã sẵn sàng. Chờ giám thị bắt đầu...", 28, true, Theme.TEXT);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setMaximumSize(new Dimension(420, 8));
        bar.setPreferredSize(new Dimension(420, 8));
        for (javax.swing.JComponent c : new javax.swing.JComponent[] {check, text, bar}) { c.setAlignmentX(CENTER_ALIGNMENT); }
        box.add(check);
        box.add(Box.createVerticalStrut(18));
        box.add(text);
        box.add(Box.createVerticalStrut(24));
        box.add(bar);
        p.add(box, new GridBagConstraints());
        return p;
    }
}
