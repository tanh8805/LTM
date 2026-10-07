// Owner: Nguoi2

package exam.client.student;

/** Những việc màn hình sinh viên có thể yêu cầu. StudentClient cài đặt. */
public interface StudentActions {

    /** Kết nối (nếu chưa) và đăng nhập. */
    void login(String studentCode, String password, String examCode);

    /** Gửi ANSWER ngay khi sinh viên chọn. choice là vị trí 0..3 trong đáp án ĐÃ TRỘN. Trả về false nếu không gửi được. */
    boolean selectAnswer(int questionId, int choice);

    /** Gửi SUBMIT. Trả về false nếu chưa thể nộp (mất kết nối, chưa thi). */
    boolean submit();

    /** Cửa sổ thi vừa mất focus (luật 5). */
    void onFocusLost();

    /** Số giây còn lại theo ĐỒNG HỒ SERVER, -1 nếu chưa có ca thi. Chỉ để hiển thị, Server mới quyết định hết giờ. */
    long getRemainingSeconds();
}
