// Owner: Nguoi2

package exam.server.db;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashDoesNotContainThePlainPassword() {
        String hash = hasher.hash("matkhau-bi-mat");

        assertFalse(hash.contains("matkhau-bi-mat"));
        assertTrue(hash.startsWith("pbkdf2$"));
    }

    @Test
    void correctPasswordVerifiesAndWrongPasswordDoesNot() {
        String hash = hasher.hash("123456");

        assertTrue(hasher.verify("123456", hash));
        assertFalse(hasher.verify("123457", hash));
        assertFalse(hasher.verify("", hash));
    }

    @Test
    void samePasswordGetsDifferentHashBecauseOfRandomSalt() {
        assertNotEquals(hasher.hash("123456"), hasher.hash("123456"));
    }

    @Test
    void garbageStoredHashNeverVerifies() {
        assertFalse(hasher.verify("123456", "123456"));
        assertFalse(hasher.verify("123456", "pbkdf2$abc$def$ghi"));
        assertFalse(hasher.verify("123456", ""));
    }
}
