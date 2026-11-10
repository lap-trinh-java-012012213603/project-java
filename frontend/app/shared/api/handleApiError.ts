import { AxiosError } from "axios";
import type { ApiErrorResponse } from "./apiEnvelope";

const ERROR_MESSAGE_MAP: Record<string, string> = {
  // Lỗi Xác thực & Quyền
  UNAUTHORIZED: "Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.",
  SEC_TOKEN_EXPIRED: "Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.",
  SEC_TOKEN_MISSING: "Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.",
  FORBIDDEN: "Bạn không có quyền thực hiện thao tác này.",
  ACCESS_DENIED: "Bạn không có quyền thực hiện thao tác này.",
  SEC_RESOURCE_NOT_OWNED: "Bạn không có quyền chỉnh sửa tài nguyên thuộc sở hữu của người dùng khác.",
  LAB_TENANT_ACCESS_DENIED: "Bạn không có quyền thao tác trên phòng Lab này.",
  SEC_LAB_TENANT_FORBIDDEN: "Bạn không có quyền thao tác trên phòng Lab này.",
  TOKEN_REUSE_DETECTED: "Cảnh báo bảo mật: Phiên truy cập bị nghi ngờ đã bị đánh cắp.",
  IAM_REFRESH_TOKEN_REUSE_DETECTED:
    "Cảnh báo bảo mật: Phiên truy cập bị nghi ngờ đã bị đánh cắp.",
  SEC_ACCOUNT_SUSPENDED: "Tài khoản của bạn hiện đang bị tạm khóa hoặc đình chỉ.",
  IAM_ACCOUNT_TEMP_LOCKED:
    "Tài khoản tạm thời bị khóa 15 phút do nhập sai mật khẩu quá 5 lần liên tiếp.",
  IAM_GOOGLE_TOKEN_INVALID:
    "Google ID Token không hợp lệ, đã hết hạn hoặc không thuộc ứng dụng AnalogHub.",

  // Lỗi Hồ sơ, Địa chỉ & Nâng cấp Vai trò (IAM)
  USER_NOT_FOUND: "Không tìm thấy hồ sơ người dùng hoặc tài khoản đã bị vô hiệu hóa.",
  IAM_USER_NOT_FOUND: "Không tìm thấy hồ sơ người dùng hoặc tài khoản đã bị vô hiệu hóa.",
  PHONE_ALREADY_EXISTS: "Số điện thoại này đã được liên kết với một tài khoản khác trên hệ thống.",
  IAM_PHONE_ALREADY_EXISTS:
    "Số điện thoại này đã được liên kết với một tài khoản khác trên hệ thống.",
  IAM_ADDRESS_LIMIT_EXCEEDED:
    "Sổ địa chỉ đã đạt giới hạn tối đa 10 địa chỉ. Vui lòng xóa bớt địa chỉ cũ trước khi thêm mới.",
  IAM_ADDRESS_NOT_FOUND: "Không tìm thấy địa chỉ hoặc địa chỉ đã bị xóa.",
  IAM_ROLE_ALREADY_GRANTED: "Bạn đã sở hữu vai trò này trên hệ thống.",
  IAM_ROLE_APPLICATION_ALREADY_PENDING:
    "Bạn đang có một hồ sơ xin nâng cấp vai trò này ở trạng thái chờ duyệt (PENDING).",

  // Lỗi Vận hành & Booking
  LAB_CAPACITY_EXCEEDED: "Phòng Lab đã đạt giới hạn công suất nhận phim trong ngày.",
  ORDER_PAYMENT_TIMEOUT: "Đơn hàng đã hết hạn thanh toán 15 phút.",
  INVALID_ROLL_STATUS_TRANSITION: "Bước chuyển trạng thái cuộn phim không hợp lệ.",
  ALREADY_CHECKED_IN: "Cuộn phim này đã được quét check-in trước đó.",

  // Lỗi Ký quỹ Escrow & Thanh toán
  INSUFFICIENT_WALLET_BALANCE: "Số dư ví khả dụng không đủ để thực hiện thanh toán.",
  ESCROW_HOLD_FROZEN: "Khoản ký quỹ đang bị đóng băng để giải quyết tranh chấp.",
  DISPUTE_WINDOW_EXPIRED: "Đã hết thời hạn 72 giờ khiếu nại đơn hàng.",

  // Lỗi AI & Hệ thống
  CV_IMAGE_UNREADABLE: "Tệp ảnh scan bị lỗi định dạng hoặc không thể phân tích độ nét.",
  RAG_NO_RELEVANT_KNOWLEDGE: "Hệ thống tri thức buồng tối chưa có thông tin về vấn đề này.",
  RATE_LIMIT_EXCEEDED: "Bạn đã thực hiện quá nhiều thao tác. Vui lòng thử lại sau 1 phút.",
  CMN_RATE_LIMIT_EXCEEDED: "Bạn đã thực hiện quá nhiều thao tác. Vui lòng thử lại sau 1 phút.",
};

export function parseApiError(error: unknown): string {
  if (error instanceof AxiosError) {
    const data = error.response?.data as ApiErrorResponse | undefined;
    const resolvedCode = data?.code || data?.errorCode;
    if (resolvedCode && ERROR_MESSAGE_MAP[resolvedCode]) {
      return ERROR_MESSAGE_MAP[resolvedCode];
    }
    if (data?.message) {
      return data.message;
    }
    if (error.response?.status === 500) {
      return "Lỗi máy chủ nội bộ. Kỹ thuật viên đã được thông báo.";
    }
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Đã có lỗi không xác định xảy ra. Vui lòng thử lại.";
}
