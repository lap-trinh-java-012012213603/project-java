# Kỹ thuật Đo sáng và Tráng Đẩy / Kéo ISO (Push / Pull Processing)

## Nguyên lý Đo sáng EI và Bản chất Quang Hóa của Push / Pull

### Sự khác biệt giữa ISO Danh định và Chỉ số Phơi sáng EI
Mỗi cuộn film xuất xưởng đều có độ nhạy sáng danh định do nhà sản xuất kiểm định theo chuẩn quốc tế ISO (ví dụ Kodak Portra 400 có ISO 400, Ilford HP5 Plus có ISO 400). Khi người chụp chủ động xoay vòng đo sáng trên máy ảnh sang một giá trị khác với ISO gốc và duy trì giá trị đó cho toàn bộ các kiểu ảnh trên cuộn film, giá trị mới được gọi là EI (Exposure Index — Chỉ số Phơi sáng). Lưu ý quan trọng: vì cả cuộn film được ngâm chung một lần trong bồn hóa chất, bạn bắt buộc phải chụp thống nhất một mức EI từ kiểu số 1 đến kiểu số 36, tuyệt đối không thể vừa chụp ISO 400 vừa push ISO 1600 xen kẽ trên cùng một cuộn film.

- **Kỹ thuật Push Processing (+1, +2, +3 stops)**: Là việc thiết lập máy ảnh đo sáng ở mức EI cao hơn ISO gốc (ví dụ chụp film ISO 400 ở EI 800 là Push +1 stop, ở EI 1600 là Push +2 stops, ở EI 3200 là Push +3 stops). Khi đó, tấm film thực chất bị thiếu sáng (underexposed) ngay tại thời điểm màn trập đóng lại. Khi đưa vào buồng tối, kỹ thuật viên kéo dài thời gian ngâm trong bể Developer để kích thích các hạt bạc đã nhận ánh sáng phát triển mạnh hơn nhằm bù đắp mật độ.
- **Kỹ thuật Pull Processing (-1, -2 stops)**: Là việc đo sáng ở mức EI thấp hơn ISO gốc (ví dụ chụp film ISO 400 ở EI 200 là Pull -1 stop, tức cố tình dư sáng +1 stop) rồi rút ngắn thời gian tráng trong bể Developer để kìm hãm vùng sáng cao không bị cháy đặc, rất hữu ích khi chụp dưới nắng gắt giữa trưa có độ tương phản cực cao.

### Ảnh hưởng đến Độ Tương phản, Chi tiết Vùng tối và Hạt Film
Về mặt vật lý quang hóa, việc kéo dài thời gian tráng khi Push không thực sự tạo ra thêm chi tiết ở những vùng tối đen chưa từng có photon ánh sáng chạm tới (theo quy tắc "Đo sáng cho vùng tối, tráng cho vùng sáng"). Do đó, khi Push +1 hoặc +2 stops, vùng tối (shadows) hầu như chỉ tăng nhẹ trong khi vùng trung tính (midtones) và vùng sáng cao (highlights) được khuếch đại mật độ rất mạnh. Kết quả là ảnh sau khi Push sẽ có độ tương phản (contrast) cao hơn hẳn, màu đen sâu và gắt hơn, các cụm hạt bạc kết tụ lại lớn hơn làm hạt grain nổi rõ rệt, và trên film màu C-41 có thể xuất hiện hiện tượng lệch màu nhẹ (color crossover) ở vùng tối.

## Bảng Công thức Bù Thời gian Tráng Push / Pull tại Film Lab

### Hệ số Bù Thời gian cho Quy trình C-41 và E-6
Trong quy trình tráng màu C-41 ở nhiệt độ chuẩn 37.8°C (thời gian gốc 3 phút 15 giây), việc điều chỉnh Push/Pull chỉ thay đổi duy nhất thời gian ở bể Color Developer, còn các bước Bleach, Fixer và Stabilizer giữ nguyên 100%:

- **Push +1 stop** (ví dụ ISO 400 chụp ở EI 800): Cộng thêm 30 giây vào thời gian Developer (tổng thời gian 3 phút 45 giây, tương đương nhân hệ số 1.15x). Các dòng film âm bản màu chuyên nghiệp như Kodak Portra 400 và Portra 800 chịu Push +1 cực kỳ xuất sắc với tông da hầu như không đổi.
- **Push +2 stops** (ví dụ ISO 400 chụp ở EI 1600): Cộng thêm 60 đến 75 giây vào thời gian Developer (tổng thời gian 4 phút 15 giây đến 4 phút 30 giây, nhân hệ số khoảng 1.35x).
- **Pull -1 stop** (ví dụ ISO 400 chụp ở EI 200): Giảm 25 đến 30 giây ở bể Developer (còn 2 phút 45 giây). Tuy nhiên với film âm bản màu C-41 có dải dung sai vùng sáng rất rộng (chịu dư sáng +2 đến +3 stops tự nhiên), người chụp thường chỉ cần chụp dư sáng +1 stop và báo Lab tráng Normal (tiêu chuẩn) để có bức ảnh mịn hạt và màu pastel dịu mắt mà không cần Pull hóa chất.
- Đối với **film dương bản Slide E-6**, thời gian điều chỉnh nằm ở bể First Developer (chuẩn 6 phút ở 38.0°C): Push +1 stop tăng lên 8 phút 00 giây; Push +2 stops tăng lên 10 phút 30 giây; Pull -1 stop giảm xuống 4 phút 30 giây.

### Hệ số Bù Thời gian cho Quy trình Đen Trắng (B&W)
Đối với film đen trắng truyền thống có cấu trúc hạt khối (cubic grain) như Kodak Tri-X 400 và Ilford HP5 Plus 400, dải chịu đựng Push rất rộng (có thể đẩy lên EI 1600 hoặc EI 3200). Quy tắc nhân hệ số thời gian tổng quát khi chưa có bảng tra cụ thể của nhà sản xuất như sau:

- **Push +1 stop**: Nhân thời gian tráng tiêu chuẩn với hệ số **1.4x** đến **1.5x** (tăng thêm 40%–50% thời gian).
- **Push +2 stops**: Nhân thời gian tráng tiêu chuẩn với hệ số **1.85x** đến **2.25x** (tùy loại thuốc hiện; nên ưu tiên dùng thuốc hiện nồng độ gốc như D-76 Stock, Ilfotec DD-X hoặc Kodak X-Tol, hạn chế dùng Rodinal pha loãng nồng độ cao nếu không muốn mất hẳn chi tiết vùng tối).
- **Pull -1 stop**: Nhân thời gian tráng tiêu chuẩn với hệ số **0.70x** đến **0.75x** (giảm khoảng 25%–30% thời gian tráng), giúp kiểm soát dải sắc độ êm mượt khi chụp chân dung hoặc phong cảnh nắng gắt.
