import React, { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  AlertTriangle,
  ArrowRight,
  Camera,
  CloudCheck,
  Eye,
  EyeOff,
  FlaskConical,
  Loader2,
  ShieldCheck,
} from "lucide-react";
import { useForm } from "react-hook-form";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router";
import { parseApiError } from "@/shared/api/handleApiError";
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
    <div className="max-w-[1040px] w-full grid grid-cols-1 lg:grid-cols-2 rounded-2xl border border-[#E7E5E4] bg-white overflow-hidden shadow-xl shadow-stone-200/50">
      {/* Left Column: Editorial Analog Photography Showcase */}
      <section className="bg-[#F7F4EF] p-8 sm:p-10 flex flex-col justify-between border-b lg:border-b-0 lg:border-r border-stone-200/80">
        <div>
          <div>
            <span className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full text-xs font-bold uppercase tracking-wider bg-[#FFF7ED] border border-[#FDBA74] text-[#C2410C]">
              <span className="w-2 h-2 rounded-full bg-[#D97706]" />
              HỆ SINH THÁI NHIẾP ẢNH PHIM ANALOG
            </span>
          </div>

          <h1 className="text-2xl md:text-3xl font-extrabold text-stone-900 leading-tight mt-4">
            Chào mừng trở lại với <span className="text-[#D97706]">AnalogHub</span>
          </h1>

          <p className="text-sm text-stone-600 leading-relaxed mt-2.5 mb-6">
            Quản lý đơn đặt tráng scan cuộn phim, lưu trữ kho ảnh số hóa và giao dịch thiết bị
            máy ảnh phim an toàn.
          </p>

          {/* Featured Visual Card with Film Rebate Header */}
          <div className="mb-7">
            <div className="bg-stone-900 text-[#F59E0B] font-mono text-[11px] px-3.5 py-1.5 flex justify-between items-center tracking-wider rounded-t-xl select-none">
              <span className="flex items-center gap-1.5 font-medium">
                <span className="inline-block w-1.5 h-3 border-x border-[#F59E0B]/50 mr-0.5" />
                FRAME 01A • KODAK PORTRA 400 • C-41 PROCESS
              </span>
              <span className="text-[10px] text-stone-400 tracking-widest hidden sm:inline">
                ▮▯▮ 36 EXP ▯▮▯
              </span>
            </div>

            <div className="w-full h-44 rounded-b-xl border-x border-b border-stone-300 shadow-sm bg-gradient-to-br from-stone-900 via-stone-800 to-amber-950 p-5 flex flex-col justify-between text-white relative overflow-hidden">
              <div className="flex items-center justify-between z-10">
                <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-white/10 backdrop-blur-xs text-[11px] font-mono text-amber-300 border border-white/15">
                  <Camera className="w-3.5 h-3.5" />
                  135mm &amp; 120 Medium Format
                </span>
                <span className="text-[11px] font-mono text-stone-300">ISO 400 • 38.0°C</span>
              </div>
              <div className="space-y-1 z-10">
                <p className="text-sm font-bold text-amber-100">
                  Contact Sheet &amp; High-Bitrate Film Archive
                </p>
                <p className="text-xs text-stone-300">
                  Kết nối trực tiếp Noritsu HS-1800 / Frontier SP-3000 từ phòng Lab đến kho ảnh cá nhân.
                </p>
              </div>
            </div>
          </div>

          {/* 3 Core Features with Colored Icon Boxes */}
          <div className="space-y-3.5">
            <div className="flex items-start gap-3.5 p-2 rounded-xl hover:bg-stone-200/40 transition-colors">
              <div className="bg-[#FFF7ED] text-[#D97706] rounded-xl p-2.5 shrink-0 shadow-2xs border border-[#FED7AA]/60 flex items-center justify-center">
                <FlaskConical className="w-5 h-5" />
              </div>
              <div className="flex-1">
                <div className="flex items-center gap-2 flex-wrap">
                  <h3 className="text-sm font-bold text-stone-900 leading-snug">
                    Đặt lịch tráng &amp; scan tại các Film Lab uy tín
                  </h3>
                  <span className="bg-[#FFFBEB] text-[#B45309] text-[10px] font-semibold px-2 py-0.5 rounded border border-[#FDE68A]">
                    Quy trình C-41 / B&amp;W / ECN-2
                  </span>
                </div>
                <p className="text-xs text-stone-500 mt-0.5">
                  Theo dõi thời gian thực từng bước từ nhận cuộn phim đến trả ảnh scan.
                </p>
              </div>
            </div>

            <div className="flex items-start gap-3.5 p-2 rounded-xl hover:bg-stone-200/40 transition-colors">
              <div className="bg-[#ECFDF5] text-[#059669] rounded-xl p-2.5 shrink-0 shadow-2xs border border-[#A7F3D0]/60 flex items-center justify-center">
                <ShieldCheck className="w-5 h-5" />
              </div>
              <div className="flex-1">
                <div className="flex items-center gap-2 flex-wrap">
                  <h3 className="text-sm font-bold text-stone-900 leading-snug">
                    Mua bán máy ảnh, ống kính qua ký quỹ Escrow 100%
                  </h3>
                  <span className="bg-[#ECFDF5] text-[#047857] text-[10px] font-semibold px-2 py-0.5 rounded border border-[#A7F3D0]">
                    Đảm bảo trả hàng
                  </span>
                </div>
                <p className="text-xs text-stone-500 mt-0.5">
                  Tiền chỉ giải ngân khi người mua kiểm tra máy đúng như thẩm định mô tả.
                </p>
              </div>
            </div>

            <div className="flex items-start gap-3.5 p-2 rounded-xl hover:bg-stone-200/40 transition-colors">
              <div className="bg-[#EEF2FF] text-[#4F46E5] rounded-xl p-2.5 shrink-0 shadow-2xs border border-[#C7D2FE]/60 flex items-center justify-center">
                <CloudCheck className="w-5 h-5" />
              </div>
              <div className="flex-1">
                <div className="flex items-center gap-2 flex-wrap">
                  <h3 className="text-sm font-bold text-stone-900 leading-snug">
                    Lưu trữ kho âm bản số hóa &amp; kiểm tra chất lượng AI
                  </h3>
                  <span className="bg-[#EEF2FF] text-[#4338CA] text-[10px] font-semibold px-2 py-0.5 rounded border border-[#C7D2FE]">
                    TIFF / JPG 48-bit
                  </span>
                </div>
                <p className="text-xs text-stone-500 mt-0.5">
                  Kho lưu trữ đám mây không nén, phân tích hạt phim và cân bằng màu chi tiết.
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* Left Column Footer Status Pill */}
        <div className="pt-6 mt-6 border-t border-stone-200">
          <div className="bg-white border border-[#E7E5E4] rounded-full px-4 py-2 inline-flex items-center gap-2 shadow-2xs text-xs text-stone-700 font-medium">
            <span className="w-2.5 h-2.5 rounded-full bg-[#10B981] animate-pulse" />
            <span>42 Film Lab đối tác đang hoạt động • Bảo mật phiên đa thiết bị</span>
          </div>
        </div>
      </section>

      {/* Right Column: Clean Modern Login Form */}
      <section className="bg-white p-8 sm:p-10 flex flex-col justify-center">
        <div className="mb-6">
          <h2 className="text-2xl font-extrabold text-stone-900 mb-1 tracking-tight">
            Đăng nhập tài khoản
          </h2>
          <p className="text-sm text-stone-500">
            Nhập thông tin tài khoản của bạn để tiếp tục trải nghiệm
          </p>
        </div>

        <GoogleOAuthButton onErrorMessage={setOauthError} />

        {/* Divider */}
        <div className="relative flex items-center justify-center my-6">
          <div className="border-t border-stone-200 w-full" />
          <span className="bg-white px-3 text-[11px] font-semibold text-stone-400 tracking-wider uppercase shrink-0">
            HOẶC ĐĂNG NHẬP BẰNG EMAIL
          </span>
        </div>

        {serverError && (
          <div
            role="alert"
            className="mb-4 p-3.5 rounded-xl bg-[#FFF1F2] border border-[#FECDD3] text-[#BE123C] text-xs font-medium flex items-start gap-2.5"
          >
            <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5 text-[#E11D48]" />
            <span>{serverError}</span>
          </div>
        )}

        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
          <Input
            type="email"
            label="Địa chỉ Email"
            required
            placeholder="nhiepanh@example.com"
            autoComplete="email"
            error={errors.email?.message}
            {...register("email")}
          />

          <Input
            type={showPassword ? "text" : "password"}
            label="Mật khẩu"
            required
            labelRight={
              <span className="text-xs font-semibold text-[#D97706] hover:underline cursor-pointer">
                Quên mật khẩu?
              </span>
            }
            placeholder="Nhập mật khẩu của bạn"
            autoComplete="current-password"
            rightIcon={
              <button
                type="button"
                tabIndex={-1}
                aria-label={showPassword ? "Ẩn ký tự" : "Hiện ký tự"}
                onClick={() => setShowPassword((prev) => !prev)}
                className="text-stone-400 hover:text-stone-700 transition-colors cursor-pointer"
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            }
            error={errors.password?.message}
            {...register("password")}
          />

          {/* Device Security Info Box (deviceInfo) */}
          <div className="bg-[#ECFDF5] border border-[#A7F3D0] rounded-xl p-3 flex items-start gap-2.5 text-xs text-[#065F46] my-4 shadow-2xs">
            <ShieldCheck className="w-4 h-4 text-[#059669] shrink-0 mt-0.5" />
            <p className="leading-relaxed">
              Thiết bị hiện tại được xác thực bảo mật phiên đăng nhập đa thiết bị.
            </p>
          </div>

          <button
            type="submit"
            disabled={loginMutation.isPending}
            className="w-full py-3.5 px-6 bg-[#111111] hover:bg-stone-800 text-white font-semibold text-sm rounded-xl flex items-center justify-center gap-3 shadow-lg shadow-stone-900/15 transition group cursor-pointer active:scale-[0.99] disabled:opacity-60"
          >
            {loginMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
            <span>Đăng nhập</span>
            <span
              aria-hidden="true"
              className="bg-[#F59E0B] text-stone-900 p-1 rounded-full flex items-center justify-center group-hover:translate-x-1 transition-transform"
            >
              <ArrowRight className="w-3.5 h-3.5 stroke-[2.5]" />
            </span>
          </button>
        </form>

        <p className="text-center text-xs text-stone-500 mt-6">
          Chưa có tài khoản AnalogHub?{" "}
          <Link
            to={registerHref}
            className="text-[#D97706] font-bold hover:underline ml-1 transition-colors"
          >
            Đăng ký ngay
          </Link>
        </p>
      </section>
    </div>
  );
};
