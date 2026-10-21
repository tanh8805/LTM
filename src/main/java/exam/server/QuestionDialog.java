package exam.server;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Sửa một câu hỏi: nội dung, 2-6 lựa chọn (ô trống bị bỏ qua) và chọn đáp án đúng. */
class QuestionDialog extends JDialog {
    private static final int MAX_OPTIONS = 6;

    private final JTextArea text = new JTextArea(4, 40);
    private final JTextField[] options = new JTextField[MAX_OPTIONS];
    private final JRadioButton[] correct = new JRadioButton[MAX_OPTIONS];
    private ExamBank.Question result;

    private QuestionDialog(Window owner, ExamBank.Question question) {
        super(owner, "Câu hỏi", Dialog.ModalityType.APPLICATION_MODAL);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new java.awt.Insets(3, 3, 3, 3);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.gridy = 0; c.gridwidth = 3; c.weightx = 1;
        form.add(new JLabel("Nội dung câu hỏi"), c);
        c.gridy = 1;
        form.add(new JScrollPane(text), c);
        c.gridy = 2;
        form.add(new JLabel("Các lựa chọn (chọn nút tròn ở đáp án đúng, ô trống bị bỏ qua)"), c);
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < MAX_OPTIONS; i++) {
            options[i] = new JTextField(36);
            correct[i] = new JRadioButton();
            group.add(correct[i]);
            c.gridy = 3 + i; c.gridwidth = 1; c.gridx = 0; c.weightx = 0;
            form.add(new JLabel(String.valueOf((char) ('A' + i))), c);
            c.gridx = 1; c.weightx = 1;
            form.add(options[i], c);
            c.gridx = 2; c.weightx = 0;
            form.add(correct[i], c);
        }
        if (question != null) { fill(question); }

        JButton ok = new JButton("OK"), cancel = new JButton("Huỷ");
        ok.addActionListener(e -> accept());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(cancel);
        buttons.add(ok);
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Hiện hộp thoại; trả câu hỏi đã sửa hoặc null nếu huỷ. question = null là thêm câu mới. */
    static ExamBank.Question edit(Window owner, ExamBank.Question question) {
        QuestionDialog dialog = new QuestionDialog(owner, question);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void fill(ExamBank.Question q) {
        text.setText(q.text());
        for (int i = 0; i < q.options().size() && i < MAX_OPTIONS; i++) { options[i].setText(q.options().get(i)); }
        correct[q.answer()].setSelected(true);
    }

    private void accept() {
        List<String> given = new ArrayList<>();
        int answer = -1;
        for (int i = 0; i < MAX_OPTIONS; i++) {
            String option = options[i].getText().trim();
            if (option.isEmpty()) { continue; }
            if (correct[i].isSelected()) { answer = given.size(); }
            given.add(option);
        }
        String error = null;
        if (text.getText().isBlank()) {
            error = "Hãy nhập nội dung câu hỏi";
        } else if (given.size() < 2) {
            error = "Cần ít nhất 2 lựa chọn";
        } else if (answer < 0) {
            error = "Hãy chọn đáp án đúng (nút tròn bên phải một lựa chọn không để trống)";
        }
        if (error != null) {
            JOptionPane.showMessageDialog(this, error);
            return;
        }
        result = new ExamBank.Question(text.getText().trim(), given, answer);
        dispose();
    }
}
