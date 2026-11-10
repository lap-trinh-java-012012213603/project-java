import React, { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  AlertTriangle,
  ArrowRight,
  Check,
  Eye,
  EyeOff,
  Loader2,
  MessageSquare,
  Palette,
  X,
} from "lucide-react";
import { useForm } from "react-hook-form";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router";
import { parseApiError } from "@/shared/api/handleApiError";
import { Input } from "@/shared/components/ui/Input";
import { cn } from "@/shared/utils/cn";
import { useRegisterMutation } from "../hooks/useAuthMutations";
import {
  evaluatePasswordStrength,
  type RegisterFormValues,
  registerSchema,
} from "../schemas/auth.schema";
import {
  CHEMICAL_PROCESS_OPTIONS,
  COLOR_TONE_OPTIONS,
  FILM_FORMAT_OPTIONS,
  SHOOTING_LEVEL_OPTIONS,
} from "../schemas/profile.schema";
import {
  buildAuthLinkWithRedirect,
  resolvePostAuthRedirect,
} from "../utils/authRedirect";
import { GoogleOAuthButton } from "./GoogleOAuthButton";

const PROCESS_DOT_COLORS: Record<string, string> = {
  C41: "bg-[#F59E0B]",
  ECN2: "bg-[#E11D48]",
  BW: "bg-[#111111]",
  E6: "bg-[#0284C7]",
};

export const RegisterForm: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const [showPassword, setShowPassword] = useState(false);
  const [oauthError, setOauthError] = useState<string | null>(null);

  // Optional StylePreferencesDto state matching Stitch screen
  const [shootingLevel, setShootingLevel] = useState<string>("BEGINNER");
  const [favoriteFormats, setFavoriteFormats] = useState<string[]>(["FMT_135", "FMT_120"]);
  const [favoriteProcesses, setFavoriteProcesses] = useState<string[]>(["C41", "BW"]);
  const [preferredColorTones, setPreferredColorTones] = useState<string[]>(["WARM_PASTEL"]);
  const [favoriteCameras, setFavoriteCameras] = useState<string[]>(["Nikon FM2", "Canon AE-1"]);
  const [cameraDraft, setCameraDraft] = useState<string>("");

  const redirectTarget = resolvePostAuthRedirect(searchParams, location.state);
  const loginHref = buildAuthLinkWithRedirect("/auth/login", searchParams);

  const {
    register,
    handleSubmit,
    watch,
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

  const passwordValue = watch("password") || "";
  const criteria = evaluatePasswordStrength(passwordValue);

  const registerMutation = useRegisterMutation(() => {
    navigate(redirectTarget, { replace: true });
  });

  const toggleMultiItem = (
    list: string[],
    code: string,
    setter: (next: string[]) => void
  ) => {
    if (list.includes(code)) {
      setter(list.filter((item) => item !== code));
    } else {
      setter([...list, code]);
    }
  };

  const handleAddCameraTag = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter" || e.key === ",") {
      e.preventDefault();
      const trimmed = cameraDraft.trim().replace(/,$/, "");
      if (trimmed && !favoriteCameras.includes(trimmed)) {
        setFavoriteCameras([...favoriteCameras, trimmed]);
      }
      setCameraDraft("");
    }
  };

  const handleRemoveCameraTag = (cam: string) => {
    setFavoriteCameras(favoriteCameras.filter((item) => item !== cam));
  };

  const onSubmit = (values: RegisterFormValues) => {
    setOauthError(null);
    const pendingCam = cameraDraft.trim();
    const mergedCameras =
      pendingCam && !favoriteCameras.includes(pendingCam)
        ? [...favoriteCameras, pendingCam]
        : favoriteCameras;

    registerMutation.mutate({
      fullName: values.fullName,
      email: values.email,
      phoneNumber: values.phoneNumber,
      password: values.password,
      stylePreferences: {
        shootingLevel,
        favoriteFormats,
        favoriteProcesses,
        preferredColorTones,
        favoriteCameras: mergedCameras,
      },
    });
  };

  const serverError = registerMutation.error
    ? parseApiError(registerMutation.error)
    : oauthError;

  const passwordRules = [
    { label: "8–72 ký tự", met: criteria.lengthValid },
    { label: "Chữ hoa & chữ thường", met: criteria.caseValid },
    { label: "Ít nhất 1 chữ số", met: criteria.digitValid },
    { label: "Ký tự đặc biệt (@$!%*?&_#-)", met: criteria.specialValid },
  ];

  return (
    <div className="w-full max-w-[900px] mx-auto bg-white rounded-2xl shadow-xl shadow-stone-200/50 border border-[#EBE7DF] p-6 sm:p-10">
      {/* Step Indicator Pills */}
      <div className="flex flex-wrap gap-2.5 sm:gap-3 items-center mb-6">
        <div className="bg-[#FFF7ED] border border-[#FDBA74] text-[#C2410C] font-semibold text-xs px-3.5 py-1.5 rounded-full flex items-center gap-1.5 shadow-2xs">
          <span className="w-2 h-2 rounded-full bg-[#EA580C]" />
          <span>Bước 1: Thông tin tài khoản</span>
        </div>
        <div className="bg-[#F5F5F4] border border-[#E7E5E4] text-[#57534E] font-medium text-xs px-3.5 py-1.5 rounded-full flex items-center gap-1.5">
          <span className="w-2 h-2 rounded-full border border-[#78716C]" />
          <span>Bước 2: Gu chụp phim (Tuỳ chọn)</span>
        </div>
      </div>

      {/* Header & Lifetime Free Badge */}
      <div className="mb-6">
        <div className="flex flex-wrap items-center gap-3 mb-2">
          <h1 className="text-2xl md:text-3xl font-extrabold text-[#111111] tracking-tight">
            Tạo tài khoản AnalogHub
          </h1>
          <span className="bg-[#FEF3C7] text-[#92400E] border border-[#FDE68A] text-xs font-semibold px-2.5 py-0.5 rounded-full">
            Miễn phí trọn đời
          </span>
        </div>
        <p className="text-sm text-[#78716C]">
          Điền thông tin cơ bản để đặt tráng scan phim, mua bán ký quỹ Escrow và lưu trữ âm bản số
          hóa.
        </p>

        <div className="mt-6">
          <GoogleOAuthButton label="Đăng ký nhanh với Google" onErrorMessage={setOauthError} />
        </div>

        {/* Divider */}
        <div className="relative my-6 text-center">
          <div aria-hidden="true" className="absolute inset-0 flex items-center">
            <div className="w-full border-t border-[#E5E7EB]" />
          </div>
          <span className="relative px-4 bg-white text-xs font-semibold tracking-wider text-[#A8A29E] uppercase">
            HOẶC ĐĂNG KÝ BẰNG EMAIL
          </span>
        </div>
      </div>

      {serverError && (
        <div
          role="alert"
          className="mb-5 p-3.5 rounded-xl bg-[#FFF1F2] border border-[#FECDD3] text-[#BE123C] text-xs font-medium flex items-start gap-2.5"
        >
          <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5 text-[#E11D48]" />
          <span>{serverError}</span>
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        {/* KHỐI 1: THÔNG TIN TÀI KHOẢN BẮT BUỘC */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5 mb-5">
          <Input
            type="text"
            label="Họ và tên"
            required
            placeholder="Nguyễn Gia Huy"
            autoComplete="name"
            error={errors.fullName?.message}
            {...register("fullName")}
          />

          <Input
            type="tel"
            label="Số điện thoại di động"
            required
            placeholder="0912345678"
            autoComplete="tel"
            helperText={
              <span className="inline-flex items-center gap-1">
                <MessageSquare className="w-3 h-3 text-[#D97706]" />
                <span>Dùng để nhận SMS thông báo tiến độ cuộn phim</span>
              </span>
            }
            error={errors.phoneNumber?.message}
            {...register("phoneNumber")}
          />
        </div>

        <div className="w-full mb-5">
          <Input
            type="email"
            label="Địa chỉ Email"
            required
            placeholder="giahuy.film@example.com"
            autoComplete="email"
            error={errors.email?.message}
            {...register("email")}
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-5 mb-3">
          <Input
            type={showPassword ? "text" : "password"}
            label="Mật khẩu"
            required
            placeholder="Ít nhất 8 ký tự, gồm hoa, thường, số & ký tự đặc biệt"
            autoComplete="new-password"
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

          <Input
            type={showPassword ? "text" : "password"}
            label="Xác nhận mật khẩu"
            required
            placeholder="Nhập lại mật khẩu ở trên"
            autoComplete="new-password"
            error={errors.confirmPassword?.message}
            {...register("confirmPassword")}
          />
        </div>

        {/* 4 TAG KIỂM TRA ĐỘ MẠNH MẬT KHẨU (EMERALD GREEN KHI HỢP LỆ) */}
        <div className="flex flex-wrap gap-2 mb-6">
          {passwordRules.map((rule) => (
            <div
              key={rule.label}
              className={cn(
                "text-xs font-semibold px-3 py-1 rounded-lg flex items-center gap-1.5 border transition-colors",
                rule.met
                  ? "bg-[#ECFDF5] border-[#6EE7B7] text-[#065F46] shadow-2xs"
                  : "bg-stone-50 border-stone-200 text-stone-500"
              )}
            >
              <Check
                className={cn(
                  "w-3.5 h-3.5 shrink-0 stroke-[2.5]",
                  rule.met ? "text-[#059669]" : "text-stone-400"
                )}
              />
              <span>{rule.label}</span>
            </div>
          ))}
        </div>

        {/* KHỐI 2: SỞ THÍCH CHỤP PHIM (StylePreferencesDto) */}
        <div className="bg-[#FFFDF9] border border-[#FDE68A] rounded-2xl p-6 mb-6 shadow-2xs">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-4 border-b border-[#FDE68A]/60 mb-5">
            <div className="flex items-center gap-2">
              <Palette className="w-5 h-5 text-[#D97706]" />
              <h2 className="font-bold text-base text-[#111111]">
                Sở thích chụp phim của bạn (Không bắt buộc)
              </h2>
            </div>
            <span className="bg-[#FFF7ED] border border-[#FDBA74] text-[#C2410C] font-semibold text-xs px-3 py-1 rounded-full flex items-center gap-1.5 self-start sm:self-auto">
              ✨ Gợi ý Film Lab &amp; dòng phim chuẩn gu
            </span>
          </div>

          <div className="space-y-5">
            {/* 1. Trình độ chụp */}
            <div>
              <span className="block text-xs font-bold text-[#111111] uppercase tracking-wider mb-2.5">
                1. Trình độ chụp
              </span>
              <div className="flex flex-wrap gap-2">
                {SHOOTING_LEVEL_OPTIONS.map((lvl) => {
                  const active = shootingLevel === lvl.value;
                  return (
                    <button
                      key={lvl.value}
                      type="button"
                      onClick={() => setShootingLevel(lvl.value)}
                      className={cn(
                        "px-4 py-2 rounded-xl text-xs font-medium flex items-center gap-1.5 cursor-pointer transition-all",
                        active
                          ? "bg-[#111111] text-white border border-[#111111] shadow-2xs font-semibold"
                          : "bg-white text-[#57534E] border border-[#E5E7EB] hover:border-stone-400"
                      )}
                    >
                      {active && <span className="w-2 h-2 rounded-full bg-[#F59E0B]" />}
                      <span>{lvl.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* 2. Khổ phim yêu thích */}
            <div>
              <span className="block text-xs font-bold text-[#111111] uppercase tracking-wider mb-2.5">
                2. Khổ phim yêu thích
              </span>
              <div className="flex flex-wrap gap-2">
                {FILM_FORMAT_OPTIONS.map((fmt) => {
                  const active = favoriteFormats.includes(fmt.code);
                  return (
                    <button
                      key={fmt.code}
                      type="button"
                      onClick={() =>
                        toggleMultiItem(favoriteFormats, fmt.code, setFavoriteFormats)
                      }
                      className={cn(
                        "px-3.5 py-1.5 rounded-xl text-xs flex items-center gap-1.5 cursor-pointer transition-all",
                        active
                          ? "bg-[#FFF7ED] text-[#9A3412] border-2 border-[#D97706] font-semibold shadow-2xs"
                          : "bg-white text-[#57534E] border border-[#E5E7EB] hover:border-stone-400 font-medium"
                      )}
                    >
                      {active && <span className="w-1.5 h-1.5 rounded-full bg-[#D97706]" />}
                      <span>{fmt.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* 3. Quy trình tráng thường dùng */}
            <div>
              <span className="block text-xs font-bold text-[#111111] uppercase tracking-wider mb-2.5">
                3. Quy trình tráng thường dùng
              </span>
              <div className="flex flex-wrap gap-2">
                {CHEMICAL_PROCESS_OPTIONS.map((proc) => {
                  const active = favoriteProcesses.includes(proc.code);
                  return (
                    <button
                      key={proc.code}
                      type="button"
                      onClick={() =>
                        toggleMultiItem(favoriteProcesses, proc.code, setFavoriteProcesses)
                      }
                      className={cn(
                        "px-3.5 py-2 rounded-xl text-xs font-medium flex items-center gap-2 cursor-pointer transition-all",
                        active
                          ? "bg-[#111111] text-white border border-[#111111] shadow-2xs"
                          : "bg-white text-[#44403C] border border-[#E5E7EB] hover:border-stone-400"
                      )}
                    >
                      <span
                        className={cn(
                          "w-2.5 h-2.5 rounded-full",
                          PROCESS_DOT_COLORS[proc.code] || "bg-[#F59E0B]",
                          active && proc.code === "BW" && "bg-white border border-[#111111]"
                        )}
                      />
                      <span>{proc.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* 4. Tông màu & 5. Dòng máy */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5 pt-1">
              <div>
                <span className="block text-xs font-bold text-[#111111] uppercase tracking-wider mb-2.5">
                  4. Tông màu yêu thích
                </span>
                <div className="flex flex-wrap gap-2">
                  {COLOR_TONE_OPTIONS.map((tone) => {
                    const active = preferredColorTones.includes(tone.code);
                    return (
                      <button
                        key={tone.code}
                        type="button"
                        onClick={() =>
                          toggleMultiItem(
                            preferredColorTones,
                            tone.code,
                            setPreferredColorTones
                          )
                        }
                        className={cn(
                          "px-3 py-1.5 rounded-xl text-xs cursor-pointer transition-all",
                          active
                            ? "bg-[#FFF7ED] text-[#9A3412] border-2 border-[#D97706] font-semibold shadow-2xs"
                            : "bg-white text-[#57534E] border border-[#E5E7EB] hover:border-stone-400 font-medium"
                        )}
                      >
                        {tone.label}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div>
                <span className="block text-xs font-bold text-[#111111] uppercase tracking-wider mb-2.5">
                  5. Dòng máy ảnh yêu thích
                </span>
                <div className="min-h-11 bg-white border border-[#E5E7EB] focus-within:border-[#D97706] focus-within:ring-2 focus-within:ring-[#FEF3C7] rounded-xl p-2 flex flex-wrap items-center gap-1.5 transition-all">
                  {favoriteCameras.map((cam) => (
                    <span
                      key={cam}
                      className="bg-[#FFF7ED] border border-[#FDBA74] text-[#9A3412] text-xs px-2.5 py-1 rounded-lg inline-flex items-center gap-1.5 font-semibold"
                    >
                      <span>{cam}</span>
                      <button
                        type="button"
                        onClick={() => handleRemoveCameraTag(cam)}
                        className="text-[#C2410C] hover:opacity-75 focus:outline-none cursor-pointer"
                        aria-label={`Xóa máy ảnh ${cam}`}
                      >
                        <X className="w-3 h-3" />
                      </button>
                    </span>
                  ))}
                  <input
                    type="text"
                    value={cameraDraft}
                    onChange={(e) => setCameraDraft(e.target.value)}
                    onKeyDown={handleAddCameraTag}
                    placeholder="Thêm máy ảnh (Enter)..."
                    className="border-0 bg-transparent text-xs p-1 text-[#111111] placeholder-[#A8A29E] focus:ring-0 focus:outline-none min-w-[120px] flex-grow"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* PHẦN CHÂN FORM & CTA */}
        <div className="pt-2">
          <div className="flex items-start gap-3 mb-6">
            <input
              id="register-terms"
              type="checkbox"
              defaultChecked
              className="w-4 h-4 mt-0.5 accent-[#D97706] rounded border-stone-300 cursor-pointer"
            />
            <label
              htmlFor="register-terms"
              className="text-xs text-[#57534E] leading-relaxed cursor-pointer"
            >
              Tôi đồng ý với{" "}
              <span className="text-[#111111] underline font-semibold">Điều khoản dịch vụ</span> và{" "}
              <span className="text-[#111111] underline font-semibold">Chính sách bảo mật</span> của
              AnalogHub
            </label>
          </div>

          <button
            type="submit"
            disabled={registerMutation.isPending}
            className="h-[52px] w-full bg-[#111111] hover:bg-black text-white text-base font-bold rounded-xl flex items-center justify-center gap-3 shadow-lg shadow-stone-900/15 tracking-wide transition-all cursor-pointer active:scale-[0.99] disabled:opacity-60"
          >
            {registerMutation.isPending && <Loader2 className="w-5 h-5 animate-spin" />}
            <span>Tạo tài khoản</span>
            <span
              aria-hidden="true"
              className="w-6 h-6 rounded-full bg-[#F59E0B] text-[#111111] flex items-center justify-center font-bold text-xs shadow-2xs"
            >
              <ArrowRight className="w-3.5 h-3.5 stroke-[2.5]" />
            </span>
          </button>

          <div className="text-center mt-4">
            <span className="text-xs text-[#78716C]">Đã có tài khoản? </span>
            <Link
              to={loginHref}
              className="text-xs font-bold text-[#D97706] hover:underline transition-all"
            >
              Đăng nhập tại đây
            </Link>
          </div>
        </div>
      </form>
    </div>
  );
};
