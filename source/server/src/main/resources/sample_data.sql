-- Owner: Nguoi2
--
-- Dữ liệu mẫu: 1 giáo viên, 10 sinh viên, 30 câu hỏi kiến thức chung.
-- Chỉ nạp khi bảng teachers còn trống (xem Database.loadSampleDataIfEmpty).
-- Tài khoản mẫu: xem docs/SPEC.md, mục "Dữ liệu mẫu".
-- TODO(Nguoi2): Mật khẩu đang lưu dạng chữ thường cho skeleton, đổi sang lưu hash.
-- Không viết dấu chấm phẩy bên trong nội dung câu hỏi (file được tách câu lệnh theo dấu chấm phẩy).

INSERT INTO teachers (username, password, full_name) VALUES ('gv01', 'teacher123', 'Giảng viên Mẫu');

INSERT INTO students (username, password, full_name) VALUES
    ('SV001', '123456', 'Nguyễn Văn An'),
    ('SV002', '123456', 'Trần Thị Bình'),
    ('SV003', '123456', 'Lê Hoàng Cường'),
    ('SV004', '123456', 'Phạm Minh Đức'),
    ('SV005', '123456', 'Hoàng Thu Hà'),
    ('SV006', '123456', 'Vũ Quang Huy'),
    ('SV007', '123456', 'Đặng Thị Lan'),
    ('SV008', '123456', 'Bùi Văn Long'),
    ('SV009', '123456', 'Ngô Thị Mai'),
    ('SV010', '123456', 'Đỗ Quốc Nam');

INSERT INTO questions (content, option_a, option_b, option_c, option_d, correct_option) VALUES
    ('Thủ đô của Việt Nam là thành phố nào?', 'Hà Nội', 'Huế', 'Đà Nẵng', 'Cần Thơ', 0),
    ('Quốc khánh nước Cộng hòa Xã hội Chủ nghĩa Việt Nam là ngày nào?', '30/4', '2/9', '1/5', '7/5', 1),
    ('Vịnh nào của Việt Nam được UNESCO công nhận là Di sản thiên nhiên thế giới?', 'Vịnh Nha Trang', 'Vịnh Cam Ranh', 'Vịnh Hạ Long', 'Vịnh Vân Phong', 2),
    ('Ngọn núi cao nhất Việt Nam là núi nào?', 'Núi Bà Đen', 'Núi Ngọc Linh', 'Núi Tam Đảo', 'Fansipan', 3),
    ('Thủ đô của Nhật Bản là thành phố nào?', 'Tokyo', 'Osaka', 'Kyoto', 'Seoul', 0),
    ('Thủ đô của nước Pháp là thành phố nào?', 'Lyon', 'Paris', 'Marseille', 'Nice', 1),
    ('Hành tinh nào gần Mặt Trời nhất?', 'Sao Kim', 'Trái Đất', 'Sao Thủy', 'Sao Hỏa', 2),
    ('Hành tinh nào lớn nhất trong Hệ Mặt Trời?', 'Trái Đất', 'Sao Hỏa', 'Sao Thổ', 'Sao Mộc', 3),
    ('Công thức hóa học của nước là gì?', 'H2O', 'CO2', 'O2', 'NaCl', 0),
    ('Con người cần khí nào để hô hấp?', 'Nitơ', 'Cacbon đioxit', 'Oxi', 'Heli', 2),
    ('Một năm thường (không nhuận) có bao nhiêu ngày?', '365', '364', '366', '360', 0),
    ('Một tuần có mấy ngày?', '5', '6', '7', '8', 2),
    ('Châu lục nào lớn nhất thế giới?', 'Châu Phi', 'Châu Âu', 'Châu Mỹ', 'Châu Á', 3),
    ('Đại dương nào lớn nhất thế giới?', 'Thái Bình Dương', 'Đại Tây Dương', 'Ấn Độ Dương', 'Bắc Băng Dương', 0),
    ('Ai là tác giả của Truyện Kiều?', 'Nguyễn Trãi', 'Nguyễn Du', 'Nguyễn Khuyến', 'Hồ Xuân Hương', 1),
    ('Chủ tịch Hồ Chí Minh đọc Tuyên ngôn Độc lập năm 1945 tại đâu?', 'Quảng trường Lam Sơn', 'Quảng trường Ba Đình', 'Hồ Hoàn Kiếm', 'Dinh Độc Lập', 1),
    ('Việt Nam thống nhất đất nước vào năm nào?', '1954', '1973', '1975', '1986', 2),
    ('5 x 6 bằng bao nhiêu?', '25', '35', '36', '30', 3),
    ('Căn bậc hai của 81 là bao nhiêu?', '7', '8', '9', '10', 2),
    ('Số nguyên tố nhỏ nhất là số nào?', '1', '2', '3', '5', 1),
    ('CPU là viết tắt của cụm từ nào?', 'Central Print Utility', 'Computer Personal Unit', 'Core Power Unit', 'Central Processing Unit', 3),
    ('Đơn vị nhỏ nhất để đo lượng thông tin trong máy tính là gì?', 'Byte', 'Bit', 'Kilobyte', 'Megabyte', 1),
    ('Hệ điều hành nào do Microsoft phát triển?', 'Linux', 'macOS', 'Android', 'Windows', 3),
    ('Kim loại nào ở thể lỏng ở nhiệt độ phòng?', 'Sắt', 'Đồng', 'Thủy ngân', 'Nhôm', 2),
    ('Ai là người đề xuất thuyết tương đối?', 'Isaac Newton', 'Galileo Galilei', 'Marie Curie', 'Albert Einstein', 3),
    ('Mặt Trăng quay quanh hành tinh nào?', 'Trái Đất', 'Sao Hỏa', 'Sao Kim', 'Sao Thủy', 0),
    ('Động vật nào lớn nhất hiện nay trên Trái Đất?', 'Cá voi xanh', 'Voi châu Phi', 'Hươu cao cổ', 'Cá mập voi', 0),
    ('Cơ quan nào bơm máu đi khắp cơ thể?', 'Phổi', 'Gan', 'Thận', 'Tim', 3),
    ('Môn thể thao nào được gọi là "môn thể thao vua"?', 'Bóng chuyền', 'Bóng rổ', 'Bóng đá', 'Cầu lông', 2),
    ('Thế vận hội Olympic mùa hè được tổ chức bao nhiêu năm một lần?', '2 năm', '3 năm', '4 năm', '5 năm', 2);
