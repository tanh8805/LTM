package exam;

import exam.server.StudentList;

/** Danh sách sinh viên cho test: server không còn cho đăng nhập khi chưa nạp danh sách. */
public final class TestStudents {
    public static final String PASSWORD = "pw";

    private TestStudents() {}

    public static StudentList of(String... ids) {
        StringBuilder csv = new StringBuilder("studentId,name,password\n");
        for (String id : ids) {
            csv.append(id).append(",Sinh viên ").append(id).append(',').append(PASSWORD).append('\n');
        }
        return StudentList.parse(csv.toString());
    }
}
