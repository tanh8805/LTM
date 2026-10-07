-- Owner: Nguoi2
--
-- Schema SQLite của hệ thống. Chạy mỗi lần Server khởi động (CREATE TABLE IF NOT EXISTS nên chạy lại an toàn).
-- Lưu ý cho Database.runSqlResource(): file được tách câu lệnh bằng dấu chấm phẩy,
-- nên KHÔNG viết dấu chấm phẩy bên trong chuỗi hoặc comment.

CREATE TABLE IF NOT EXISTS teachers (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    username  TEXT NOT NULL UNIQUE,
    password  TEXT NOT NULL,
    full_name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS students (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    username  TEXT NOT NULL UNIQUE,
    password  TEXT NOT NULL,
    full_name TEXT NOT NULL
);

-- Ngân hàng câu hỏi: 4 đáp án, correct_option = 0 (A), 1 (B), 2 (C), 3 (D)
CREATE TABLE IF NOT EXISTS questions (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    content        TEXT NOT NULL,
    option_a       TEXT NOT NULL,
    option_b       TEXT NOT NULL,
    option_c       TEXT NOT NULL,
    option_d       TEXT NOT NULL,
    correct_option INTEGER NOT NULL CHECK (correct_option BETWEEN 0 AND 3)
);

-- Đề thi và các câu trong đề
CREATE TABLE IF NOT EXISTS exams (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    title              TEXT NOT NULL,
    duration_minutes   INTEGER NOT NULL,
    created_by_teacher INTEGER REFERENCES teachers(id)
);

CREATE TABLE IF NOT EXISTS exam_questions (
    exam_id     INTEGER NOT NULL REFERENCES exams(id),
    question_id INTEGER NOT NULL REFERENCES questions(id),
    PRIMARY KEY (exam_id, question_id)
);

-- Ca thi: status = CREATED, RUNNING hoặc ENDED. start_time là epoch milli giây.
-- rules_json: luật giám sát giáo viên cấu hình cho ca thi (JSON của MonitoringRules)
CREATE TABLE IF NOT EXISTS exam_shifts (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    code             TEXT NOT NULL UNIQUE,
    exam_id          INTEGER NOT NULL REFERENCES exams(id),
    start_time       INTEGER NOT NULL,
    duration_minutes INTEGER NOT NULL,
    status           TEXT NOT NULL DEFAULT 'CREATED',
    rules_json       TEXT
);

-- Thí sinh của ca thi. shuffle_seed: hạt giống trộn câu/đáp án riêng từng người,
-- lưu lại để reconnect vẫn ra đúng thứ tự cũ.
CREATE TABLE IF NOT EXISTS shift_students (
    shift_id     INTEGER NOT NULL REFERENCES exam_shifts(id),
    student_id   INTEGER NOT NULL REFERENCES students(id),
    shuffle_seed INTEGER,
    PRIMARY KEY (shift_id, student_id)
);

-- Đáp án sinh viên chọn. choice là vị trí 0..3 trong thứ tự ĐÃ TRỘN của sinh viên đó.
CREATE TABLE IF NOT EXISTS answers (
    shift_id    INTEGER NOT NULL REFERENCES exam_shifts(id),
    student_id  INTEGER NOT NULL REFERENCES students(id),
    question_id INTEGER NOT NULL REFERENCES questions(id),
    choice      INTEGER NOT NULL,
    answered_at INTEGER NOT NULL,
    PRIMARY KEY (shift_id, student_id, question_id)
);

CREATE TABLE IF NOT EXISTS results (
    shift_id     INTEGER NOT NULL REFERENCES exam_shifts(id),
    student_id   INTEGER NOT NULL REFERENCES students(id),
    score        REAL NOT NULL,
    submitted_at INTEGER NOT NULL,
    PRIMARY KEY (shift_id, student_id)
);

-- Vi phạm giám sát (Nguoi3 ghi vào đây)
CREATE TABLE IF NOT EXISTS violations (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    shift_id       INTEGER,
    student_id     INTEGER NOT NULL REFERENCES students(id),
    violation_type TEXT NOT NULL,
    evidence       TEXT,
    time           INTEGER NOT NULL
);
