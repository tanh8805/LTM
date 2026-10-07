// Owner: Nguoi1

package exam.common.model;

/**
 * Cách dùng ML, đọc từ config/ml.properties (khóa ml.mode).
 *
 * NONE     : không dùng ML
 * IF       : chỉ Isolation Forest (chạy ở client)
 * CHRONOS  : chỉ Chronos-Bolt (chạy ở ml-service, server gọi)
 * BOTH_OR  : đáng ngờ nếu IF HOẶC Chronos báo bất thường
 * BOTH_AND : đáng ngờ nếu IF VÀ Chronos cùng báo bất thường
 */
public enum MlMode {
    NONE,
    IF,
    CHRONOS,
    BOTH_OR,
    BOTH_AND
}
