// Màn kết quả cuối cùng, hoặc màn thông báo ngắn (đang nộp bài / bài thi đã kết thúc) (Người 1).
package exam.client;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

class ResultPanel extends JPanel {
    private final JPanel box = new JPanel();

    ResultPanel() {
        super(new GridBagLayout());
        setBackground(Theme.BACKGROUND);
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        add(box, new GridBagConstraints());
    }

    /** "8.5 / 10": luôn dùng dấu chấm, không theo ngôn ngữ máy. */
    static String scoreText(double score) {
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(score) + " / 10";
    }

    void showMessage(String text) {
        fill(Theme.label(text, 28, true, Theme.TEXT));
    }

    void showResult(double score, int correct, int total) {
        fill(Theme.label("Kết quả bài thi", 20, false, Theme.MUTED), Theme.label(scoreText(score), 72, true, Theme.PRIMARY),
            Theme.label("Đúng " + correct + "/" + total + " câu", 24, false, Theme.TEXT),
            Theme.label("Cảm ơn bạn đã hoàn thành bài thi. Chúc bạn kết quả tốt!", Theme.BASE_SIZE, false, Theme.MUTED));
    }

    private void fill(JComponent... parts) {
        box.removeAll();
        for (JComponent c : parts) {
            c.setAlignmentX(CENTER_ALIGNMENT);
            box.add(c);
            box.add(Box.createVerticalStrut(14));
        }
        box.revalidate();
        box.repaint();
    }
}
