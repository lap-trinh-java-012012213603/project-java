import { RegisterForm } from "@/features/iam/components/RegisterForm";

export function meta() {
  return [
    { title: "Đăng Ký Tài Khoản — AnalogHub" },
    {
      name: "description",
      content: "Tạo tài khoản AnalogHub để đặt tráng scan phim, lưu trữ ảnh số và giao dịch Escrow.",
    },
  ];
}

export default function RegisterPage() {
  return <RegisterForm />;
}
