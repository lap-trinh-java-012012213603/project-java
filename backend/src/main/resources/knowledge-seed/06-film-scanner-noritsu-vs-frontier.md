# So sánh Máy Scan Film Noritsu HS-1800 và Fujifilm Frontier SP-3000

## Kiến trúc Quang học và Đặc trưng Màu sắc (Color Science)

### Máy Scan Noritsu Koki HS-1800: Chất Màu Trung tính, Trong trẻo và Dải Tương phản Rộng
Noritsu Koki HS-1800 là dòng máy scan film tốc độ cao chuyên nghiệp được sử dụng rộng rãi tại các Film Lab hiện đại ở Việt Nam. Máy sử dụng nguồn sáng LED RGB cường độ cao kết hợp hệ thống cảm biến CCD tuyến tính (Linear Array CCD) quét liên tục khi dải film di chuyển qua cổng giữ film (film carrier) tự động cho khổ 135 và 120. Đặc trưng nổi bật nhất của thuật toán giải mã màu (Color Inversion Profile) trên phần mềm EZ Controller của Noritsu là tính trung thực, trong trẻo và ít áp đặt màu sắc nhân tạo lên phôi film gốc.

Khi scan các dòng film chân dung như Kodak Portra 160, Portra 400 hoặc Kodak Gold 200 trên Noritsu HS-1800, bức ảnh giữ được tông màu pastel nhẹ nhàng, vùng chuyển sáng tối (roll-off) cực kỳ mềm mại và bảo toàn tối đa chi tiết ở cả vùng tối sâu (shadows) lẫn vùng sáng cao (highlights). Đặc biệt, Noritsu HS-1800 hỗ trợ xuất trực tiếp định dạng file ảnh không nén **16-bit TIFF** ở độ phân giải cao (lên tới 6774 × 4492 pixels cho khổ 135), tạo ra tệp dữ liệu có biên độ hậu kỳ (latitude) rất lớn cho các nhiếp ảnh gia muốn tự cân chỉnh màu sắc trên Adobe Lightroom hoặc Capture One.

### Máy Scan Fujifilm Frontier SP-3000: Chất Màu Ấm Áp, Tương phản Nổi khối và Tông Da Đặc trưng
Fujifilm Frontier SP-3000 là huyền thoại minilab sử dụng cảm biến Area CCD kết hợp hệ thống buồng khuếch tán ánh sáng LED tích hợp gương cầu và phần mềm xử lý ảnh độc quyền **Image Intelligence** của Fujifilm (chạy trên nền tảng MS01 / FE). Khác với triết lý trung tính của Noritsu, thuật toán của Fuji Frontier can thiệp sâu vào việc tách lớp màu sắc, tăng độ tương phản cục bộ (micro-contrast) và định hình bảng màu mang đậm chất điện ảnh hoài cổ.

Ảnh xuất ra từ Fuji Frontier SP-3000 thường có độ tương phản đanh chắc, màu đen sâu thẳm, sắc xanh lá cây (foliage greens) chuyển sang tông xanh ngọc lục bảo trầm ấm và tông da người (skin tones) hồng hào rạng rỡ ngay từ file JPEG gốc mà hầu như không cần hậu kỳ thêm. Cấu trúc hạt film (grain) trên Frontier có xu hướng mịn và sắc cạnh hơn ở vùng trung tính, tuy nhiên vùng sáng cao (highlights) có thể cắt gắt sớm hơn một chút so với Noritsu nếu cuộn film chụp bị thiếu sáng hoặc quá tráng.

## Công nghệ Khử bụi Hồng ngoại Digital ICE và Kinh nghiệm Chọn Máy Scan

### Nguyên lý Hoạt động của Kênh Hồng ngoại Digital ICE và Giới hạn Kỹ thuật
Cả Noritsu HS-1800 và Fujifilm Frontier SP-3000 đều được trang bị kênh quét hồng ngoại phần cứng gọi là **Digital ICE (Image Correction and Enhancement)**. Khi quét một khung hình film màu (C-41 hoặc E-6), sau khi thu nhận ba kênh màu Đỏ, Xanh lá, Xanh dương (RGB), máy phát ra một chùm tia hồng ngoại bước sóng dài đi xuyên qua đế film. Do các lớp thuốc nhuộm màu hữu cơ trong film C-41 và E-6 hoàn toàn trong suốt đối với tia hồng ngoại, bất kỳ vật cản nào chặn tia hồng ngoại đều được máy xác định chính xác là hạt bụi bề mặt, sợi vải hoặc vết xước vật lý trên lớp gelatin, từ đó phần mềm tự động nội suy xóa sạch vết bụi mà không làm mờ chi tiết ảnh xung quanh.

**Quy tắc bắt buộc đối với Film Đen trắng (B&W)**: Tuyệt đối phải **tắt tính năng Digital ICE** khi scan các dòng film đen trắng truyền thống (như Kodak Tri-X, Ilford HP5, T-Max, Fomapan) hoặc film màu Kodachrome. Lý do là hình ảnh trên film đen trắng được cấu tạo từ các hạt **bạc kim loại (metallic silver)** thật sự chứ không phải thuốc nhuộm hữu cơ. Hạt bạc kim loại sẽ phản xạ và chặn tia hồng ngoại giống hệt như hạt bụi, khiến thuật toán Digital ICE nhận diện nhầm chính các đường nét chi tiết của bức ảnh là bụi bẩn và xóa bệt toàn bộ bức ảnh tạo ra các mảngartifact biến dạng nặng nề. (Ngoại lệ duy nhất là dòng film đen trắng tráng quy trình C-41 như Ilford XP2 Super vì cấu tạo bằng thuốc nhuộm nên vẫn bật được Digital ICE).

### Gợi ý Lựa chọn Máy Scan Theo Dòng Film và Mục đích Sử dụng
- **Nên chọn Scan trên Fujifilm Frontier SP-3000 khi**: Bạn chụp ảnh đời thường, đường phố, du lịch hoặc chân dung với các dòng film phổ thông như Fujifilm C200, Superia X-Tra 400, Kodak ColorPlus 200, Ultramax 400 và muốn nhận ngay file ảnh JPEG có màu sắc rực rỡ, tương phản nổi khối "ăn liền" để chia sẻ lên mạng xã hội.
- **Nên chọn Scan trên Noritsu HS-1800 khi**: Bạn chụp trong điều kiện ánh sáng chênh lệch gắt, chụp đêm với CineStill 800T / Vision3 500T, chụp khổ trung bình Medium Format 120 (6×6, 6×7), chụp film đen trắng B&W muốn giữ nguyên kết cấu hạt bạc tự nhiên, hoặc khi bạn cần đặt gói Scan chất lượng cao (High-Res / TIFF 16-bit) để in triển lãm khổ lớn và tự hậu kỳ màu sắc chuyên sâu.
