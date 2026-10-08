// Owner: Nguoi3

package exam.tools;

import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Tạo hai cửa sổ Swing thật ("Exam" và "Other") để đo sự kiện mất focus bằng ĐÚNG cơ chế của StudentFrame:
 * WindowFocusListener.windowLostFocus() của cửa sổ thi. Dùng khi thu dữ liệu trên máy không có người dùng
 * (chạy dưới Xvfb): mỗi lần triggerFocusLoss() chuyển focus sang cửa sổ khác rồi quay lại, tức là một lần mất focus thật
 * theo hệ thống cửa sổ (không phải tăng biến đếm trực tiếp).
 *
 * Cần DISPLAY (ví dụ Xvfb). Bộ đếm là TÍCH LŨY giống focusLostCount của client thật.
 */
public class FocusProbe {

    private static final int SWITCH_PAUSE_MS = 300;

    private final AtomicInteger lostCount = new AtomicInteger();
    private JFrame examWindow;
    private JFrame otherWindow;

    /** Mở hai cửa sổ và cho cửa sổ thi giữ focus. Ném IllegalStateException nếu không có màn hình. */
    public void open() throws InterruptedException, InvocationTargetException {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("FocusProbe cần DISPLAY (chạy dưới Xvfb hoặc màn hình thật)");
        }
        SwingUtilities.invokeAndWait(() -> {
            examWindow = new JFrame("Exam");
            examWindow.setSize(300, 200);
            examWindow.setLocation(20, 50);
            examWindow.addWindowFocusListener(new WindowFocusListener() {
                @Override
                public void windowGainedFocus(WindowEvent event) {
                }

                @Override
                public void windowLostFocus(WindowEvent event) {
                    lostCount.incrementAndGet();
                }
            });
            otherWindow = new JFrame("Other");
            otherWindow.setSize(300, 200);
            otherWindow.setLocation(340, 50);
            examWindow.setVisible(true);
            otherWindow.setVisible(true);
        });
        Thread.sleep(1000);
        bringToFront(examWindow);
        Thread.sleep(SWITCH_PAUSE_MS);
        lostCount.set(0); // lần mất focus lúc mở cửa sổ không tính
    }

    /** Một lần mất focus: chuyển sang cửa sổ khác rồi quay lại cửa sổ thi. */
    public void triggerFocusLoss() throws InterruptedException, InvocationTargetException {
        focusAndWait(otherWindow);
        Thread.sleep(SWITCH_PAUSE_MS);
        focusAndWait(examWindow);
        Thread.sleep(SWITCH_PAUSE_MS);
    }

    /** Yêu cầu focus rồi đợi tới khi cửa sổ thật sự có focus (thử lại, vì không có window manager nên đôi khi yêu cầu bị bỏ qua). */
    private void focusAndWait(JFrame window) throws InterruptedException, InvocationTargetException {
        for (int attempt = 0; attempt < 20; attempt++) {
            bringToFront(window);
            Thread.sleep(100);
            if (window.isFocused()) {
                return;
            }
        }
        System.out.println("[FocusProbe] Cảnh báo: cửa sổ " + window.getTitle() + " không nhận được focus sau 20 lần thử");
    }

    public int getLostCount() {
        return lostCount.get();
    }

    public void close() throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            examWindow.dispose();
            otherWindow.dispose();
        });
    }

    private void bringToFront(JFrame window) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            window.toFront();
            window.requestFocus();
        });
    }
}
