// Màn làm bài: lưới số câu bên trái, câu hỏi và các lựa chọn dạng hàng ở giữa, nút điều hướng và gợi ý phím ở dưới, phím tắt (Người 1).
package exam.client;

import exam.common.Html;
import exam.common.Icons;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;

class ExamPanel extends JPanel {
    private final IntConsumer onGoto, onAnswer;
    private final JPanel grid = new JPanel(new GridLayout(0, 5, 6, 6));
    private final List<NumberButton> numbers = new ArrayList<>();
    private final JLabel doneLabel = Theme.label(" ", 13, false, Theme.MUTED);
    private final JLabel numberLabel = Theme.label(" ", 12, true, Theme.MUTED);
    private final JTextArea questionText = new JTextArea();
    private final JPanel optionsPanel = new JPanel();
    private final List<OptionRow> rows = new ArrayList<>();
    private int currentIndex;

    ExamPanel(IntConsumer onGoto, Runnable onPrev, Runnable onNext, IntConsumer onAnswer) {
        super(new BorderLayout());
        this.onGoto = onGoto;
        this.onAnswer = onAnswer;
        setBackground(Color.WHITE);
        add(sidebar(), BorderLayout.WEST);
        add(main(onPrev, onNext), BorderLayout.CENTER);
        keys(onPrev, onNext);
    }

    /** Cột trái: "CÂU HỎI" + "Đã làm x/n", lưới số câu, chú thích màu và dòng báo đang ghi nhận. */
    private JPanel sidebar() {
        JPanel side = new JPanel(new BorderLayout(0, 14));
        side.setBackground(new Color(0xF7F8FB));
        side.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER),
            BorderFactory.createEmptyBorder(20, 20, 18, 20)));
        side.setPreferredSize(new Dimension(280, 0));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(Theme.label("CÂU HỎI", 12, true, Theme.MUTED), BorderLayout.WEST);
        header.add(doneLabel, BorderLayout.EAST);
        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(header, BorderLayout.NORTH);
        grid.setOpaque(false);
        JPanel gridBox = new JPanel(new BorderLayout());
        gridBox.setOpaque(false);
        gridBox.add(grid, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(gridBox);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        top.add(scroll, BorderLayout.CENTER);
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        legend.setOpaque(false);
        legend.add(legendItem(Theme.PRIMARY, "Đã làm"));
        legend.add(legendItem(Color.WHITE, "Chưa làm"));
        top.add(legend, BorderLayout.SOUTH);
        side.add(top, BorderLayout.CENTER);
        JLabel watching = new JLabel(Html.wrap("Đang ghi nhận: ứng dụng, mạng, USB, rời cửa sổ thi", 200),
            Icons.eye(18, Theme.MUTED), SwingConstants.LEFT);
        watching.setFont(Theme.font(13, false));
        watching.setForeground(Theme.MUTED);
        watching.setIconTextGap(10);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        watching.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        bottom.add(watching);
        side.add(bottom, BorderLayout.SOUTH);
        return side;
    }

    private static JLabel legendItem(Color fill, String text) {
        JLabel l = Theme.label(text, 13, false, Theme.MUTED);
        l.setIcon(new javax.swing.Icon() {
            @Override public int getIconWidth() { return 12; }
            @Override public int getIconHeight() { return 12; }
            @Override
            public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(fill);
                g2.fillRoundRect(x, y, 11, 11, 4, 4);
                g2.setColor(fill == Color.WHITE ? Theme.BORDER : fill);
                g2.drawRoundRect(x, y, 11, 11, 4, 4);
                g2.dispose();
            }
        });
        l.setIconTextGap(6);
        return l;
    }

    private JPanel main(Runnable onPrev, Runnable onNext) {
        questionText.setEditable(false);
        questionText.setFocusable(false);
        questionText.setBorder(null);
        questionText.setMargin(new java.awt.Insets(0, 0, 0, 0));
        questionText.setOpaque(false);
        questionText.setLineWrap(true);
        questionText.setWrapStyleWord(true);
        questionText.setFont(Theme.font(20, true));
        questionText.setForeground(Theme.TEXT);
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setOpaque(false);
        JPanel question = new JPanel(new BorderLayout(0, 8));
        question.setOpaque(false);
        question.add(numberLabel, BorderLayout.NORTH);
        question.add(questionText, BorderLayout.CENTER);
        JPanel content = new ScrollContent();
        content.setOpaque(false);
        content.setLayout(new BorderLayout(0, 20));
        content.add(question, BorderLayout.NORTH);
        content.add(optionsPanel, BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JButton prev = new JButton("← Câu trước"), next = new JButton("Câu sau →");
        for (JButton b : new JButton[] {prev, next}) {
            b.setFont(Theme.font(14, true));
            b.setMargin(new java.awt.Insets(8, 18, 8, 18));
            b.setFocusable(false);
        }
        prev.addActionListener(e -> onPrev.run());
        next.addActionListener(e -> onNext.run());
        JPanel nav = new JPanel(new BorderLayout());
        nav.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        left.add(prev);
        left.add(next);
        nav.add(left, BorderLayout.WEST);
        nav.add(Theme.label("Phím A–D chọn đáp án · ← → chuyển câu", 13, false, Theme.MUTED), BorderLayout.EAST);
        JPanel p = new JPanel(new BorderLayout(0, 16));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(28, 44, 24, 44));
        p.add(scroll, BorderLayout.CENTER);
        p.add(nav, BorderLayout.SOUTH);
        return p;
    }

    /** Phím 1-9 hoặc A-I chọn đáp án, mũi tên trái/phải chuyển câu. Chỉ hoạt động khi màn này đang hiện. */
    private void keys(Runnable onPrev, Runnable onNext) {
        for (int i = 0; i < 9; i++) {
            int choice = i;
            AbstractAction pick = new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { choose(choice); }
            };
            bind(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, 0), "pick" + i, pick);
            bind(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD1 + i, 0), "pickpad" + i, pick);
            bind(KeyStroke.getKeyStroke(KeyEvent.VK_A + i, 0), "pickletter" + i, pick);
        }
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "prev", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onPrev.run(); }
        });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "next", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onNext.run(); }
        });
    }

    private void bind(KeyStroke key, String name, AbstractAction action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(key, name);
        getActionMap().put(name, action);
    }

    private void choose(int choice) {
        if (choice >= rows.size()) { return; }
        rows.forEach(r -> r.setSelected(false));
        rows.get(choice).setSelected(true);
        onAnswer.accept(choice);
    }

    /** Hiện một câu hỏi; selected là chỉ số lựa chọn đã chọn trước đó, -1 nếu chưa chọn. */
    void showQuestion(int number, int total, String text, List<String> options, int selected) {
        currentIndex = number - 1;
        numberLabel.setText("CÂU " + number + " / " + total);
        questionText.setText(text);
        if (numbers.size() != total) { buildGrid(total); }
        for (int i = 0; i < numbers.size(); i++) { numbers.get(i).setCurrent(i == currentIndex); }
        optionsPanel.removeAll();
        rows.clear();
        for (int i = 0; i < options.size(); i++) {
            OptionRow row = new OptionRow((char) ('A' + i), options.get(i), i == selected, this::choose, i);
            rows.add(row);
            optionsPanel.add(row);
            optionsPanel.add(javax.swing.Box.createVerticalStrut(12));
        }
        optionsPanel.revalidate();
        optionsPanel.repaint();
    }

    /** Cập nhật màu các nút số và dòng "Đã làm x/n". */
    void setAnswered(boolean[] answered) {
        int done = 0;
        for (int i = 0; i < numbers.size() && i < answered.length; i++) {
            numbers.get(i).setDone(answered[i]);
            if (answered[i]) { done++; }
        }
        doneLabel.setText("Đã làm " + done + "/" + numbers.size());
    }

    private void buildGrid(int total) {
        grid.removeAll();
        numbers.clear();
        for (int i = 0; i < total; i++) {
            NumberButton b = new NumberButton(i + 1);
            int index = i;
            b.addActionListener(e -> onGoto.accept(index));
            numbers.add(b);
            grid.add(b);
        }
        doneLabel.setText("Đã làm 0/" + total);
        grid.revalidate();
    }

    /** Nội dung cuộn: luôn rộng bằng khung nhìn để chữ tự xuống dòng thay vì tràn ngang. */
    private static class ScrollContent extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 120; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
