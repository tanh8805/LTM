// Owner: Nguoi2

package exam.server.db;

/** Một thí sinh của ca thi. */
public class CandidateRecord {
    public int studentId;
    /** Mã sinh viên, cũng là machineId. */
    public String username;
    public String fullName;
    /** Hạt giống trộn câu hỏi và đáp án riêng cho sinh viên này. */
    public long shuffleSeed;
}
