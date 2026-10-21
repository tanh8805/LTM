// Bảng màu và khoảng cách chung cho giao diện sinh viên và giám thị: chỉ chứa hằng số.
package exam.common;

import java.awt.Color;

public final class Palette {
    // Một màu chính (xanh dương), chữ xám đậm, nền xám rất nhạt; cảnh báo và lỗi dịu
    public static final Color PRIMARY = new Color(0x2F6FDE), PRIMARY_SOFT = new Color(0xE8F0FD), PRIMARY_TEXT = new Color(0x1E4DA8),
        WARNING = new Color(0xB7791F), WARNING_SOFT = new Color(0xFFF4DC), WARNING_TEXT = new Color(0x7A5200),
        ERROR = new Color(0xC8453B), ERROR_SOFT = new Color(0xFCEAE8), ERROR_TEXT = new Color(0x9B2C24),
        SUCCESS = new Color(0x2E8B57), SUCCESS_SOFT = new Color(0xE6F4EC), SUCCESS_TEXT = new Color(0x1B6B40),
        BACKGROUND = new Color(0xF5F6F8), CARD = Color.WHITE, TEXT = new Color(0x374151), MUTED = new Color(0x6B7280),
        BORDER = new Color(0xE0E4EA), SEPARATOR = new Color(0xEAEDF1),
        NEUTRAL_SOFT = new Color(0xEDEFF3), NEUTRAL_TEXT = new Color(0x4B5563), DISABLED = new Color(0xA9B4C6),
        ATTENTION = new Color(0xFFF6E8), SELECTED = new Color(0xE4EDFD), TRACK = new Color(0xE6E9EF), BAR = new Color(0x8E95A3);

    // Lưới 8px và bo góc 12px
    public static final int GAP = 8, RADIUS = 12;

    private Palette() {}
}
