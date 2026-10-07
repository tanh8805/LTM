// Owner: Nguoi2

package exam.server.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import exam.common.model.Question;
import exam.common.model.Role;
import exam.common.model.UserAccount;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Kiểm tra schema.sql + sample_data.sql: tạo đúng 1 giáo viên, 10 sinh viên, 30 câu hỏi. */
class DatabaseInitTest {

    @TempDir
    Path tempDirectory;

    private Database createDatabaseWithSampleData() throws Exception {
        Database database = new Database(tempDirectory.resolve("test-exam.db").toString());
        database.createTables();
        database.loadSampleDataIfEmpty();
        return database;
    }

    @Test
    void sampleDataHasExpectedRowCounts() throws Exception {
        Database database = createDatabaseWithSampleData();

        assertEquals(1, new UserDao(database).countByRole(Role.TEACHER));
        assertEquals(10, new UserDao(database).countByRole(Role.STUDENT));
        assertEquals(30, new QuestionDao(database).countAll());
    }

    @Test
    void runningInitTwiceDoesNotDuplicateSampleData() throws Exception {
        Database database = createDatabaseWithSampleData();

        database.createTables();
        database.loadSampleDataIfEmpty();

        assertEquals(10, new UserDao(database).countByRole(Role.STUDENT));
        assertEquals(30, new QuestionDao(database).countAll());
    }

    @Test
    void everySampleQuestionHasFourOptionsAndValidCorrectIndex() throws Exception {
        Database database = createDatabaseWithSampleData();

        List<Question> questions = new QuestionDao(database).findAll();

        assertEquals(30, questions.size());
        for (Question question : questions) {
            assertEquals(4, question.options.size());
            assertEquals(true, question.correctIndex >= 0 && question.correctIndex <= 3);
        }
    }

    @Test
    void loginWithSampleAccounts() throws Exception {
        UserDao userDao = new UserDao(createDatabaseWithSampleData());

        UserAccount student = userDao.findByUsernameAndPassword(Role.STUDENT, "SV001", "123456");
        assertNotNull(student);
        assertEquals("SV001", student.username);
        assertEquals(Role.STUDENT, student.role);

        UserAccount teacher = userDao.findByUsernameAndPassword(Role.TEACHER, "gv01", "teacher123");
        assertNotNull(teacher);
        assertEquals(Role.TEACHER, teacher.role);
    }

    @Test
    void loginFailsWithWrongPasswordOrWrongRole() throws Exception {
        UserDao userDao = new UserDao(createDatabaseWithSampleData());

        assertNull(userDao.findByUsernameAndPassword(Role.STUDENT, "SV001", "wrong-password"));
        // SV001 là sinh viên, đăng nhập bằng vai trò giáo viên phải thất bại
        assertNull(userDao.findByUsernameAndPassword(Role.TEACHER, "SV001", "123456"));
    }
}
