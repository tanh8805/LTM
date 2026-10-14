// Test các hàm thuần của giao diện client (hướng dẫn PRECHECK, màu đồng hồ, định dạng điểm, câu hỏi nộp bài); không mở cửa sổ.
package exam.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class UiTextTest {
    @Test
    void precheckHintsTellWhatToDo() {
        assertEquals("Đóng ứng dụng chrome.exe", PrecheckPanel.hint("Đang mở ứng dụng bị cấm: chrome.exe"));
        assertEquals("Đóng ứng dụng figma (Figma)", PrecheckPanel.hint("Hãy đóng ứng dụng: figma (Figma)"));
        assertEquals("Rút USB ở ổ E:", PrecheckPanel.hint("Đang cắm USB: E:"));
        assertEquals("Hãy xử lý vấn đề này", PrecheckPanel.hint("Lỗi lạ"));
    }

    @Test
    void timerColorGoesGreenOrangeRed() {
        assertEquals(Theme.SUCCESS, Theme.timerColor(3600));
        assertEquals(Theme.SUCCESS, Theme.timerColor(300));
        assertEquals(Theme.WARNING, Theme.timerColor(299));
        assertEquals(Theme.WARNING, Theme.timerColor(60));
        assertEquals(Theme.ERROR, Theme.timerColor(59));
        assertEquals(Theme.ERROR, Theme.timerColor(0));
    }

    @Test
    void scoreUsesVietnameseComma() {
        assertEquals("5,5", exam.common.Format.score(5.5));
        assertEquals("10,0", exam.common.Format.score(10.0));
        assertEquals("3,33", exam.common.Format.score(3.33));
        assertEquals("0,0", exam.common.Format.score(0));
    }

    @Test
    void submitMessageListsUnansweredQuestions() {
        assertEquals("Bạn đã làm 13/20 câu. Còn 7 câu chưa làm: 9, 14, 16, 17, 18, 19, 20. Sau khi nộp sẽ không sửa được.",
            StudentScreens.submitMessage(20, List.of(9, 14, 16, 17, 18, 19, 20)));
        assertEquals("Bạn đã làm 20/20 câu. Sau khi nộp sẽ không sửa được.", StudentScreens.submitMessage(20, List.of()));
    }

    @Test
    void exitMessageWarnsAboutLosingAnswers() {
        assertEquals("Đáp án chỉ lưu trong ứng dụng này. Nếu thoát bây giờ, 13 câu đã làm sẽ mất. Bạn có thể nộp bài rồi thoát.",
            StudentScreens.exitMessage(13));
    }

    @Test
    void precheckListsFailuresThenPassedChecks() {
        List<PrecheckPanel.Item> items = PrecheckPanel.items(
            List.of("Đang mở ứng dụng bị cấm: Safari, Zalo", "Đang cắm USB: KINGSTON"), "192.168.43.20:5050");
        assertEquals(3, items.size());
        assertFalse(items.get(0).ok());
        assertEquals("Đang mở ứng dụng bị cấm: Safari, Zalo", items.get(0).title());
        assertEquals("Đóng ứng dụng Safari, Zalo", items.get(0).hint());
        assertFalse(items.get(1).ok());
        assertTrue(items.get(2).ok());
        assertEquals("Đã kết nối máy giám thị 192.168.43.20:5050", items.get(2).title());
        assertEquals(2, PrecheckPanel.items(List.of("Đang cắm USB: E:"), "x:1").stream().filter(PrecheckPanel.Item::ok).count(),
            "Không có vấn đề về ứng dụng thì có dòng đã đạt cho ứng dụng");
        assertEquals("Còn 2 việc cần làm. Danh sách tự cập nhật khi bạn xử lý xong.", PrecheckPanel.subtitle(2));
        assertEquals("Tự kiểm tra lại sau 4 giây", PrecheckPanel.countdownText(4));
    }

    @Test
    void submittedAtText() {
        assertEquals("Đã nộp bài lúc 10:05:43", ResultPanel.submittedText("10:05:43"));
    }
}
