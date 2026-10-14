// Hộp xác nhận kiểu "tấm" hiện ở giữa phía trên cửa sổ: tiêu đề, nội dung và hai nút (Người 1).
package exam.client;

import exam.common.Html;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

final class Sheet {
    private Sheet() {}

    /** Kết quả: nút phụ (trái), nút chính (phải) hoặc đóng bằng Esc. */
    enum Choice { SECONDARY, PRIMARY, CANCEL }

    /** Hộp thoại nhận cửa sổ chính làm cha để việc mở nó không bị tính là rời cửa sổ thi. */
    static Choice ask(Window owner, String title, String message, String secondary, String primary) {
        JDialog dialog = new JDialog(owner, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        Choice[] result = {Choice.CANCEL};
        JButton second = new JButton(secondary), first = Theme.primaryButton(primary);
        second.setMargin(new java.awt.Insets(8, 18, 8, 18));
        first.setMargin(new java.awt.Insets(8, 18, 8, 18));
        second.addActionListener(e -> { result[0] = Choice.SECONDARY; dialog.dispose(); });
        first.addActionListener(e -> { result[0] = Choice.PRIMARY; dialog.dispose(); });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(second);
        buttons.add(first);
        JLabel body = Theme.label(Html.wrap(message, 420), 14, false, Theme.MUTED);
        JPanel box = new JPanel(new BorderLayout(0, 12));
        box.setBackground(Color.WHITE);
        box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER, 1),
            BorderFactory.createEmptyBorder(22, 24, 18, 24)));
        box.add(Theme.label(title, 18, true, Theme.TEXT), BorderLayout.NORTH);
        box.add(body, BorderLayout.CENTER);
        box.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(box);
        dialog.getRootPane().setDefaultButton(first);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setLocation(owner.getX() + (owner.getWidth() - dialog.getWidth()) / 2, owner.getY() + 70);
        dialog.setVisible(true);
        return result[0];
    }
}
