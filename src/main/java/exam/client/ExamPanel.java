// Màn làm bài: lưới số câu bên trái, câu hỏi và các lựa chọn dạng hàng lớn ở giữa, nút điều hướng ở dưới, phím tắt (Người 1).
package exam.client;

import java.awt.BorderLayout;
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
import javax.swing.KeyStroke;
import javax.swing.Scrollable;

class ExamPanel extends JPanel {
    private final IntConsumer onGoto, onAnswer;
    private final JPanel grid = new JPanel(new GridLayout(0, 5, 6, 6));
    private final List<NumberButton> numbers = new ArrayList<>();
    private final JLabel doneLabel = Theme.label(" ", 14, true, Theme.MUTED);
    private final JLabel numberLabel = Theme.label(" ", 20, true, Theme.PRIMARY);
    private final JTextArea questionText = new JTextArea();
    private final JPanel optionsPanel = new JPanel();
    private final List<OptionRow> rows = new ArrayList<>();
    private int currentIndex;

    ExamPanel(IntConsumer onGoto, Runnable onPrev, Runnable onNext, Runnable onSubmit, IntConsumer onAnswer) {
        super(new BorderLayout(16, 0));
        this.onGoto = onGoto;
        this.onAnswer = onAnswer;
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        add(sidebar(), BorderLayout.WEST);
        add(main(onPrev, onNext, onSubmit), BorderLayout.CENTER);
        keys(onPrev, onNext);
    }

    private JPanel sidebar() {
        Theme.Card card = new Theme.Card();
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        card.setPreferredSize(new Dimension(300, 0));
        card.add(Theme.label("Danh sách câu hỏi", 16, true, Theme.TEXT), BorderLayout.NORTH);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        grid.setOpaque(false);
        holder.add(grid, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        card.add(scroll, BorderLayout.CENTER);
        card.add(doneLabel, BorderLayout.SOUTH);
        return card;
    }

    private JPanel main(Runnable onPrev, Runnable onNext, Runnable onSubmit) {
        questionText.setEditable(false);
        questionText.setFocusable(false);
        questionText.setOpaque(false);
        questionText.setLineWrap(true);
        questionText.setWrapStyleWord(true);
        questionText.setFont(Theme.font(19, false));
        questionText.setForeground(Theme.TEXT);
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setOpaque(false);
        Theme.Card card = new Theme.Card();
        card.setLayout(new BorderLayout(0, 12));
        card.add(numberLabel, BorderLayout.NORTH);
        card.add(questionText, BorderLayout.CENTER);
        JPanel content = new ScrollContent();
        content.setOpaque(false);
        content.setLayout(new BorderLayout(0, 14));
        content.add(card, BorderLayout.NORTH);
        content.add(optionsPanel, BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JButton prev = new JButton("Câu trước"), next = new JButton("Câu sau"), submit = Theme.primaryButton("Nộp bài");
        submit.setBackground(Theme.SUCCESS);
        prev.setFont(Theme.font(Theme.BASE_SIZE, true));
        next.setFont(Theme.font(Theme.BASE_SIZE, true));
        prev.setMargin(new java.awt.Insets(10, 22, 10, 22));
        next.setMargin(new java.awt.Insets(10, 22, 10, 22));
        prev.setFocusable(false);
        next.setFocusable(false);
        prev.addActionListener(e -> onPrev.run());
        next.addActionListener(e -> onNext.run());
        submit.addActionListener(e -> onSubmit.run());
        JPanel nav = new JPanel(new BorderLayout());
        nav.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        left.add(prev);
        left.add(next);
        nav.add(left, BorderLayout.WEST);
        nav.add(submit, BorderLayout.EAST);
        JPanel p = new JPanel(new BorderLayout(0, 14));
        p.setOpaque(false);
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
        numberLabel.setText("Câu " + number + "/" + total);
        questionText.setText(text);
        if (numbers.size() != total) { buildGrid(total); }
        for (int i = 0; i < numbers.size(); i++) { numbers.get(i).setCurrent(i == currentIndex); }
        optionsPanel.removeAll();
        rows.clear();
        for (int i = 0; i < options.size(); i++) {
            OptionRow row = new OptionRow((char) ('A' + i), options.get(i), i == selected, this::choose, i);
            rows.add(row);
            optionsPanel.add(row);
            optionsPanel.add(javax.swing.Box.createVerticalStrut(10));
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
