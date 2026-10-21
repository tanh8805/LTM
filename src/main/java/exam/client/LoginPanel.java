// Màn đăng nhập: một khung giữa màn hình, lỗi hiện chữ đỏ dưới form, đang tìm server thì hiện thanh tiến trình (Người 1).
package exam.client;

import exam.common.Html;
import exam.common.Protocol;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JTextField;

class LoginPanel extends JPanel {
    private final JTextField idField = new JTextField(), hostField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JButton loginBtn = Theme.primaryButton("Đăng nhập");
    private final JLabel errorLabel = Theme.label(" ", Theme.BASE_SIZE, false, Theme.ERROR);
    private final JLabel busyLabel = Theme.label(" ", Theme.BASE_SIZE, false, Theme.MUTED);
    private final JProgressBar progress = new JProgressBar();

    LoginPanel(Runnable onLogin) {
        super(new GridBagLayout());
        setBackground(Theme.BACKGROUND);
        hostField.putClientProperty("JTextField.placeholderText", "Để trống để tự tìm server");
        loginBtn.addActionListener(e -> onLogin.run());
        progress.setIndeterminate(true);
        progress.setVisible(false);

        Theme.Card card = new Theme.Card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(32, 40, 32, 40));
        card.setPreferredSize(new Dimension(460, 500));
        add(card, new GridBagConstraints());
        card.add(left(Theme.label(Html.wrap(Protocol.TOPIC, 380), 22, true, Theme.PRIMARY)));
        card.add(left(Theme.label("Đăng nhập để vào phòng thi", Theme.BASE_SIZE, false, Theme.MUTED)));
        card.add(Box.createVerticalStrut(22));
        field(card, "Mã sinh viên", idField);
        field(card, "Mật khẩu", passwordField);
        field(card, "IP server", hostField);
        card.add(Box.createVerticalStrut(6));
        loginBtn.setAlignmentX(LEFT_ALIGNMENT);
        loginBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        card.add(loginBtn);
        card.add(Box.createVerticalStrut(10));
        card.add(left(errorLabel));
        card.add(left(busyLabel));
        progress.setAlignmentX(LEFT_ALIGNMENT);
        card.add(progress);
    }

    private static void field(JPanel card, String caption, JTextField input) {
        card.add(left(Theme.label(caption, 14, true, Theme.TEXT)));
        card.add(Box.createVerticalStrut(4));
        input.setAlignmentX(LEFT_ALIGNMENT);
        input.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        input.setPreferredSize(new Dimension(300, 40));
        card.add(input);
        card.add(Box.createVerticalStrut(12));
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(LEFT_ALIGNMENT);
        return c;
    }

    String studentId() { return idField.getText().trim(); }

    String password() { return new String(passwordField.getPassword()).trim(); }

    String host() { return hostField.getText().trim(); }

    JButton button() { return loginBtn; }

    /** Đang tìm server/kết nối: khoá nút và hiện thanh tiến trình. */
    void setBusy(boolean busy, String text) {
        loginBtn.setEnabled(!busy);
        progress.setVisible(busy);
        busyLabel.setText(busy ? text : " ");
        if (busy) { errorLabel.setText(" "); }
    }

    void setError(String text) { errorLabel.setText(Html.wrap(text, 380)); }
}
