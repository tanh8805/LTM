// Nhãn HTML tự xuống dòng trong một bề ngang cho trước.
package exam.common;

public final class Html {
    /** Swing đọc "px" trong CSS lớn hơn điểm ảnh thật khoảng 1,3 lần, nên chia lại để bề ngang đúng như mong muốn. */
    private static final double SWING_PX = 1.3;

    private Html() {}

    /** Bọc văn bản thuần (tự escape) thành HTML ngắt dòng trong widthPx điểm ảnh. */
    public static String wrap(String text, int widthPx) {
        return "<html><body style='width:" + (int) Math.round(widthPx / SWING_PX) + "px'>"
            + text.replace("&", "&amp;").replace("<", "&lt;") + "</body></html>";
    }
}
