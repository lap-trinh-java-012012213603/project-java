import { LoginForm } from "@/features/iam/components/LoginForm";

export function meta() {
  return [
    { title: "Đăng Nhập — AnalogHub" },
    {
      name: "description",
      content: "Đăng nhập vào hệ sinh thái nhiếp ảnh phim AnalogHub.",
    },
  ];
}

export default function LoginPage() {
  return <LoginForm />;
}
