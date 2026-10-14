// Định dạng điểm kiểu Việt Nam (dấu phẩy) dùng chung cho màn kết quả của sinh viên và bảng giám thị; file CSV vẫn dùng dấu chấm.
package exam.common;

public final class Format {
    private Format() {}

    /** 5.5 thành "5,5"; 9 thành "9,0"; 3.33 thành "3,33" (tối đa 2 chữ số thập phân). */
    public static String score(double score) {
        boolean oneDecimal = Math.abs(score * 10 - Math.round(score * 10)) < 1e-9;
        return String.format(java.util.Locale.ROOT, oneDecimal ? "%.1f" : "%.2f", score).replace('.', ',');
    }
}
