// Owner: Nguoi1

package exam.common.protocol;

/**
 * Lớp cha của mọi message trong protocol.
 *
 * Mỗi message gửi qua TCP là MỘT dòng JSON (kết thúc bằng '\n'), ví dụ:
 *   {"type":"HEARTBEAT","seq":3,"summary":{...}}
 *
 * Field "type" cho biết đây là message gì. MessageCodec.decode() dựa vào field này
 * để biết phải tạo ra class nào (xem MessageType).
 *
 * Các class message là "túi chứa dữ liệu" thuần: field public, không có logic,
 * để Gson đọc/ghi trực tiếp và người đọc nhìn là hiểu.
 */
public abstract class Message {

    /** Một trong các hằng số của MessageType. */
    public String type;

    protected Message(String type) {
        this.type = type;
    }
}
