// Owner: Nguoi2

package exam.server.db;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Băm mật khẩu bằng PBKDF2 (có trong JDK, không cần thư viện ngoài).
 *
 * Mật khẩu KHÔNG BAO GIỜ được lưu dạng chữ thường. Trong database lưu chuỗi:
 *   pbkdf2$<số vòng lặp>$<salt base64>$<hash base64>
 * Mỗi mật khẩu có salt ngẫu nhiên riêng nên hai người cùng mật khẩu vẫn có hash khác nhau.
 */
public class PasswordHasher {

    private static final int ITERATIONS = 10_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private final SecureRandom random = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    /** true nếu password khớp với chuỗi đã lưu. Chuỗi lưu sai định dạng thì trả false. */
    public boolean verify(String password, String storedHash) {
        String[] parts = storedHash.split("\\$");
        if (parts.length != 4 || !parts[0].equals("pbkdf2")) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(password, salt, iterations);
            // isEqual so sánh trong thời gian không phụ thuộc vào vị trí byte khác nhau.
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false; // số vòng lặp hoặc base64 hỏng
        }
    }

    private byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JDK không hỗ trợ PBKDF2WithHmacSHA256", e);
        }
    }
}
