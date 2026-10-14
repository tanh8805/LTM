// Test các hàm thuần của giao diện client (hướng dẫn PRECHECK, màu đồng hồ, định dạng điểm, câu hỏi nộp bài); không mở cửa sổ.
package exam.client;

import static org.junit.jupiter.api.Assertions.*;

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
    void scoreTextUsesDotAndOutOfTen() {
        assertEquals("8.5 / 10", ResultPanel.scoreText(8.5));
        assertEquals("10 / 10", ResultPanel.scoreText(10.0));
        assertEquals("3.33 / 10", ResultPanel.scoreText(3.3333));
    }

    @Test
    void submitQuestionMentionsUnansweredCount() {
        assertEquals("Bạn còn 2 câu chưa làm. Nộp bài?", StudentScreens.submitQuestion(2));
        assertEquals("Nộp bài ngay?", StudentScreens.submitQuestion(0));
    }
}
