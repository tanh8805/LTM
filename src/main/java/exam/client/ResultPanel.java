// Màn kết quả cuối cùng (dấu tích, giờ nộp, hai ô Điểm và Số câu đúng, lời cảm ơn, nút Thoát), hoặc thông báo ngắn (Người 1).
package exam.client;

import exam.common.Card;
import exam.common.Format;
import exam.common.Icons;
import exam.common.Palette;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

class ResultPanel extends JPanel {
    private final JPanel box = new JPanel();
    private final JButton exitBtn = Theme.primaryButton("Thoát ứng dụng");

    ResultPanel(Runnable onExit) {
        super(new GridBagLayout());
        exitBtn.addActionListener(e -> onExit.run());
        setBackground(Theme.BACKGROUND);
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        add(box, new GridBagConstraints());
    }

    static final String TERMINATED_TITLE = "Bài thi bị đình chỉ";

    static String submittedText(String time) { return "Đã nộp bài lúc " + time; }

    static String reasonText(String reason) { return "Lý do: " + reason; }

    /** Thông báo ngắn khi đang nộp bài: chưa có nút vì kết nối còn đang dùng. */
    void showMessage(String text) {
        fill(Theme.label(text, 28, true, Theme.TEXT));
    }

    void showResult(double score, int correct, int total, String submittedAt) {
        JPanel cards = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        cards.setOpaque(false);
        cards.add(card("Điểm", Format.score(score), "/ 10"));
        cards.add(card("Số câu đúng", String.valueOf(correct), "/ " + total));
        fill(new JLabel(Icons.bigCheck(64)), Theme.label(submittedText(submittedAt), 28, true, Theme.TEXT),
            Theme.label("Cảm ơn bạn đã làm bài.", Theme.BASE_SIZE, false, Theme.MUTED), cards, exitBtn);
    }

    /** Bị đình chỉ vì vi phạm luật: màn đỏ, nêu lý do, điểm 0. */
    void showTerminated(String reason, int total) {
        JPanel cards = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        cards.setOpaque(false);
        cards.add(card("Điểm", Format.score(0), "/ 10"));
        cards.add(card("Số câu đúng", "0", "/ " + total));
        fill(new JLabel(Icons.bigCross(64)), Theme.label(TERMINATED_TITLE, 28, true, Palette.ERROR),
            Theme.label(reasonText(reason), Theme.BASE_SIZE, false, Palette.ERROR_TEXT), cards, exitBtn);
    }

    private static JComponent card(String caption, String value, String suffix) {
        Card card = new Card();
        card.setLayout(new BorderLayout(0, 6));
        card.setPreferredSize(new java.awt.Dimension(230, 104));
        card.add(Theme.label(caption, 13, false, Theme.MUTED), BorderLayout.NORTH);
        JPanel number = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        number.setOpaque(false);
        number.add(Theme.label(value, 40, true, Theme.TEXT));
        number.add(Theme.label(suffix, 18, false, Theme.MUTED));
        card.add(number, BorderLayout.CENTER);
        return card;
    }

    private void fill(JComponent... parts) {
        box.removeAll();
        for (int i = 0; i < parts.length; i++) {
            parts[i].setAlignmentX(CENTER_ALIGNMENT);
            box.add(parts[i]);
            box.add(Box.createVerticalStrut(i == 0 ? 18 : 16));
        }
        box.revalidate();
        box.repaint();
    }
}
