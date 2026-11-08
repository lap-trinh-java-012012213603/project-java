import React, { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { AlertTriangle, Eye, EyeOff, Lock, LogIn, Mail } from "lucide-react";
import { useForm } from "react-hook-form";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router";
import { parseApiError } from "@/shared/api/handleApiError";
import { Button } from "@/shared/components/ui/Button";
import { Input } from "@/shared/components/ui/Input";
import { useLoginMutation } from "../hooks/useAuthMutations";
import { type LoginFormValues, loginSchema } from "../schemas/auth.schema";
import {
  buildAuthLinkWithRedirect,
  getBrowserDeviceInfo,
  resolvePostAuthRedirect,
} from "../utils/authRedirect";
import { GoogleOAuthButton } from "./GoogleOAuthButton";

export const LoginForm: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const [showPassword, setShowPassword] = useState(false);
  const [oauthError, setOauthError] = useState<string | null>(null);

  const redirectTarget = resolvePostAuthRedirect(searchParams, location.state);
  const registerHref = buildAuthLinkWithRedirect("/auth/register", searchParams);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: "",
      password: "",
    },
  });

  const loginMutation = useLoginMutation(() => {
    navigate(redirectTarget, { replace: true });
  });

  const onSubmit = (values: LoginFormValues) => {
    setOauthError(null);
    loginMutation.mutate({
      email: values.email,
      password: values.password,
      deviceInfo: getBrowserDeviceInfo(),
    });
  };

  const serverError = loginMutation.error
    ? parseApiError(loginMutation.error)
    : oauthError;

  return (
    <div className="space-y-6">
      <div className="space-y-1.5 text-center">
        <h1 className="text-xl font-bold tracking-tight text-gray-100">
          Đăng Nhập Buồng Tối
        </h1>
        <p className="text-xs text-gray-400">
          Truy cập kho lưu trữ phim số hóa, đặt lịch tráng scan và chợ thiết bị Escrow.
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
          type="email"
          label="Email"
          placeholder="minh.nguyen@analoghub.vn"
          autoComplete="email"
          leftIcon={<Mail className="w-4 h-4" />}
          error={errors.email?.message}
          {...register("email")}
        />

        <Input
          type={showPassword ? "text" : "password"}
          label="Mật khẩu"
          placeholder="Nhập mật khẩu của bạn"
          autoComplete="current-password"
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

        <Button
          type="submit"
          variant="primary"
          className="w-full"
          isLoading={loginMutation.isPending}
          leftIcon={<LogIn className="w-4 h-4" />}
        >
          Đăng nhập
        </Button>
      </form>

      <div className="relative">
        <div className="absolute inset-0 flex items-center">
          <div className="w-full border-t border-darkroom-800" />
        </div>
        <div className="relative flex justify-center text-[11px] uppercase font-mono">
          <span className="bg-darkroom-900 px-3 text-gray-500">Hoặc đăng nhập nhanh</span>
        </div>
      </div>

      <GoogleOAuthButton onErrorMessage={setOauthError} />

      <p className="text-center text-xs text-gray-400">
        Chưa có tài khoản AnalogHub?{" "}
        <Link
          to={registerHref}
          className="font-semibold text-kodak-amber hover:text-kodak-gold hover:underline"
        >
          Đăng ký ngay
        </Link>
      </p>
    </div>
  );
};
