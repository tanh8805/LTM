// Màn kết quả cuối cùng (dấu tích, giờ nộp, hai ô Điểm và Số câu đúng, nút Thoát/Thi lượt mới), hoặc thông báo ngắn (Người 1).
package exam.client;

import exam.common.Format;
import exam.common.Icons;
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
    private final JButton newRoundBtn = new JButton("Thi lượt mới"), exitBtn = Theme.primaryButton("Thoát ứng dụng");

    ResultPanel(Runnable onNewRound, Runnable onExit) {
        super(new GridBagLayout());
        newRoundBtn.setMargin(new java.awt.Insets(10, 24, 10, 24));
        newRoundBtn.setFont(Theme.font(Theme.BASE_SIZE, true));
        newRoundBtn.addActionListener(e -> onNewRound.run());
        exitBtn.addActionListener(e -> onExit.run());
        setBackground(Theme.BACKGROUND);
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        add(box, new GridBagConstraints());
    }

    static String submittedText(String time) { return "Đã nộp bài lúc " + time; }

    /** Thông báo ngắn khi đang nộp bài: chưa có nút vì kết nối còn đang dùng. */
    void showMessage(String text) {
        fill(Theme.label(text, 28, true, Theme.TEXT));
    }

    /** Bài thi đã kết thúc mà máy này không có đề để nộp: cho phép đăng nhập lại kỳ mới. */
    void showEnded(String text) {
        fill(Theme.label(text, 28, true, Theme.TEXT), newRoundBtn);
    }

    void showResult(double score, int correct, int total, String submittedAt) {
        JPanel cards = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        cards.setOpaque(false);
        cards.add(card("Điểm", Format.score(score), "/ 10"));
        cards.add(card("Số câu đúng", String.valueOf(correct), "/ " + total));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.setOpaque(false);
        buttons.add(exitBtn);
        buttons.add(newRoundBtn);
        fill(new JLabel(Icons.bigCheck(64)), Theme.label(submittedText(submittedAt), 28, true, Theme.TEXT),
            Theme.label("Giám thị đã nhận bài của bạn.", Theme.BASE_SIZE, false, Theme.MUTED), cards, buttons,
            Theme.label("Bây giờ có thể thoát an toàn, kể cả bằng Alt+F4.", 13, false, Theme.MUTED));
    }

    private static JComponent card(String caption, String value, String suffix) {
        Theme.Card card = new Theme.Card();
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
