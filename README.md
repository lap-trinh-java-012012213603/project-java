#  Java Project

Mô tả ngắn gọn về dự án của bạn (Ví dụ: Hệ thống quản lý bán hàng / Nền tảng học trực tuyến). Dự án được xây dựng với kiến trúc Client-Server sử dụng Spring Boot cho Backend và ReactJS (TypeScript) cho Frontend.

##  Công nghệ sử dụng

### Backend
* **Ngôn ngữ:** Java 21
* **Framework:** Spring Boot
* **Build Tool:** Maven (hoặc Gradle)
* **Database:** (Điền tên DB của bạn: MySQL, PostgreSQL, SQL Server...)

### Frontend
* **Core:** ReactJS
* **Ngôn ngữ:** TypeScript
* **Build Tool:** Vite (hoặc Create React App)
* **Styling:** (Tailwind CSS, SCSS, hoặc Material UI...)

---

## Yêu cầu môi trường (Prerequisites)

Đảm bảo máy tính của bạn đã cài đặt các phần mềm sau:
* [JDK 21](https://adoptium.net/)
* [Node.js](https://nodejs.org/) (phiên bản 18.x hoặc mới hơn)
* (Tùy chọn) Docker để chạy Database nhanh chóng.

---

## Hướng dẫn cài đặt và chạy dự án (Getting Started)

### 1. Cài đặt Backend (Spring Boot)
Di chuyển vào thư mục backend và chạy ứng dụng:

```bash
cd backend

# Nếu dùng Maven
./mvnw clean install
./mvnw spring-boot:run

# Nếu dùng Gradle
./gradlew build
./gradlew bootRun