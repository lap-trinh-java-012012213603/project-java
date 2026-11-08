import React, { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { AlertTriangle, Eye, EyeOff, Lock, Mail, Phone, User, UserPlus } from "lucide-react";
import { useForm } from "react-hook-form";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router";
import { parseApiError } from "@/shared/api/handleApiError";
import { Button } from "@/shared/components/ui/Button";
import { Input } from "@/shared/components/ui/Input";
import { useRegisterMutation } from "../hooks/useAuthMutations";
import { type RegisterFormValues, registerSchema } from "../schemas/auth.schema";
import {
  buildAuthLinkWithRedirect,
  resolvePostAuthRedirect,
} from "../utils/authRedirect";
import { GoogleOAuthButton } from "./GoogleOAuthButton";

export const RegisterForm: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const [showPassword, setShowPassword] = useState(false);
  const [oauthError, setOauthError] = useState<string | null>(null);

  const redirectTarget = resolvePostAuthRedirect(searchParams, location.state);
  const loginHref = buildAuthLinkWithRedirect("/auth/login", searchParams);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      fullName: "",
      email: "",
      phoneNumber: "",
      password: "",
      confirmPassword: "",
    },
  });

  const registerMutation = useRegisterMutation(() => {
    navigate(redirectTarget, { replace: true });
  });

  const onSubmit = (values: RegisterFormValues) => {
    setOauthError(null);
    registerMutation.mutate({
      fullName: values.fullName,
      email: values.email,
      phoneNumber: values.phoneNumber,
      password: values.password,
    });
  };

  const serverError = registerMutation.error
    ? parseApiError(registerMutation.error)
    : oauthError;

  return (
    <div className="space-y-6">
      <div className="space-y-1.5 text-center">
        <h1 className="text-xl font-bold tracking-tight text-gray-100">
          Khởi Tạo Tài Khoản Nhiếp Ảnh Phim
        </h1>
        <p className="text-xs text-gray-400">
          Lưu trữ cuộn phim chất lượng gốc, nhận thông báo tiến độ Lab và giao dịch an toàn.
        </p>
      </div>

      {serverError && (
        <div
          role="alert"
          className="p-3 rounded-[var(--radius-film)] bg-safelight-subtle border border-safelight-red/40 text-safelight-red text-xs flex items-start gap-2"
        >
          <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
          <span>{serverError}</span>
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        <Input
          type="text"
          label="Họ và tên"
          placeholder="Nguyễn Quang Minh"
          autoComplete="name"
          leftIcon={<User className="w-4 h-4" />}
          error={errors.fullName?.message}
          {...register("fullName")}
        />

        <Input
          type="email"
          label="Email"
          placeholder="minh.nguyen@analoghub.vn"
          autoComplete="email"
          leftIcon={<Mail className="w-4 h-4" />}
          error={errors.email?.message}
          {...register("email")}
        />

        <Input
          type="tel"
          label="Số điện thoại"
          placeholder="0908123456"
          autoComplete="tel"
          leftIcon={<Phone className="w-4 h-4" />}
          error={errors.phoneNumber?.message}
          {...register("phoneNumber")}
        />

        <Input
          type={showPassword ? "text" : "password"}
          label="Mật khẩu"
          placeholder="Ít nhất 8 ký tự, gồm hoa, thường, số & ký tự đặc biệt"
          autoComplete="new-password"
          leftIcon={<Lock className="w-4 h-4" />}
          rightIcon={
            <button
              type="button"
              tabIndex={-1}
              aria-label={showPassword ? "Ẩn ký tự" : "Hiện ký tự"}
              onClick={() => setShowPassword((prev) => !prev)}
              className="text-gray-400 hover:text-gray-200 transition-colors"
            >
              {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          }
          error={errors.password?.message}
          {...register("password")}
        />

        <Input
          type={showPassword ? "text" : "password"}
          label="Xác nhận mật khẩu"
          placeholder="Nhập lại mật khẩu ở trên"
          autoComplete="new-password"
          leftIcon={<Lock className="w-4 h-4" />}
          error={errors.confirmPassword?.message}
          {...register("confirmPassword")}
        />

        <Button
          type="submit"
          variant="primary"
          className="w-full"
          isLoading={registerMutation.isPending}
          leftIcon={<UserPlus className="w-4 h-4" />}
        >
          Tạo tài khoản
        </Button>
      </form>

      <div className="relative">
        <div className="absolute inset-0 flex items-center">
          <div className="w-full border-t border-darkroom-800" />
        </div>
        <div className="relative flex justify-center text-[11px] uppercase font-mono">
          <span className="bg-darkroom-900 px-3 text-gray-500">Hoặc đăng ký nhanh</span>
        </div>
      </div>

      <GoogleOAuthButton label="Đăng ký nhanh với Google" onErrorMessage={setOauthError} />

      <p className="text-center text-xs text-gray-400">
        Đã có tài khoản?{" "}
        <Link
          to={loginHref}
          className="font-semibold text-kodak-amber hover:text-kodak-gold hover:underline"
        >
          Đăng nhập
        </Link>
      </p>
    </div>
  );
};
