// Owner: Nguoi1

package exam.common.model;

/**
 * Chế độ gửi số liệu giám sát của một máy (xem SET_RATE).
 *
 * NORMAL   : summary nằm trong HEARTBEAT, mỗi 10 giây.
 * HIGH     : gửi METRICS_DETAIL mỗi 2 giây. Tự về NORMAL sau 60 giây yên.
 * BASELINE : mọi máy gửi METRICS_DETAIL mỗi 1 giây, không chạy ML (dùng để thu dữ liệu thực nghiệm).
 */
public enum RateMode {
    NORMAL,
    HIGH,
    BASELINE
}
