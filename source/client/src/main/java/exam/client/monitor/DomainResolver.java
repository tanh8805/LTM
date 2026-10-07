// Owner: Nguoi3

package exam.client.monitor;

import java.util.List;

/** Đổi tên miền thành danh sách địa chỉ IP. Tách ra để test không cần mạng thật. */
public interface DomainResolver {

    /** Trả về các IP của domain, hoặc danh sách rỗng nếu không resolve được. */
    List<String> resolve(String domain);
}
