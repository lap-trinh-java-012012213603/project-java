import React, { useRef, useState } from "react";
import {
  AlertTriangle,
  Award,
  Camera,
  Check,
  CheckCircle2,
  Clock,
  LogOut,
  Mail,
  Phone,
  Power,
  Sparkles,
  User as UserIcon,
  X,
} from "lucide-react";
import { parseApiError } from "@/shared/api/handleApiError";
import { Badge } from "@/shared/components/ui/Badge";
import { Button } from "@/shared/components/ui/Button";
import { Input } from "@/shared/components/ui/Input";
import { cn } from "@/shared/utils/cn";
import { useLogoutMutation } from "../hooks/useAuthMutations";
import {
  useMyProfileQuery,
  useMyRoleApplicationsQuery,
  useUpdateProfileMutation,
  useUploadAvatarMutation,
} from "../hooks/useProfileQueries";
import {
  CHEMICAL_PROCESS_OPTIONS,
  COLOR_TONE_OPTIONS,
  FILM_FORMAT_OPTIONS,
  SHOOTING_LEVEL_OPTIONS,
  updateProfileSchema,
} from "../schemas/profile.schema";
import type { RoleApplicationStatus } from "../types/iam.types";
import { AvatarCropModal } from "./AvatarCropModal";
import { RoleApplicationModal } from "./RoleApplicationModal";

const PROCESS_DOT_COLORS: Record<string, string> = {
  C41: "bg-[#F59E0B]",
  ECN2: "bg-[#E11D48]",
  BW: "bg-[#111111]",
  E6: "bg-[#0284C7]",
};

export interface UserProfileProps {
  onOpenRoleModalExternal?: () => void;
}

export const UserProfile: React.FC<UserProfileProps> = () => {
  const { data: profile, isLoading } = useMyProfileQuery();
  const { data: roleApplications = [] } = useMyRoleApplicationsQuery();
  const logoutMutation = useLogoutMutation();

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [fullName, setFullName] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [avatarKey, setAvatarKey] = useState("");
  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState<string | null>(null);
  const [pendingAvatarFile, setPendingAvatarFile] = useState<File | null>(null);
  const [isCropModalOpen, setIsCropModalOpen] = useState(false);
  const [favoriteFormats, setFavoriteFormats] = useState<string[]>([]);
  const [favoriteProcesses, setFavoriteProcesses] = useState<string[]>([]);
  const [preferredColorTones, setPreferredColorTones] = useState<string[]>([]);
  const [favoriteCamerasInput, setFavoriteCamerasInput] = useState("");
  const [shootingLevel, setShootingLevel] = useState("INTERMEDIATE");

  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [isRoleModalOpen, setIsRoleModalOpen] = useState(false);
  const [roleStatusFilter, setRoleStatusFilter] = useState<"ALL" | RoleApplicationStatus>("ALL");
  const [syncedProfileKey, setSyncedProfileKey] = useState<string | null>(null);

  const profileSyncKey = profile ? `${profile.id}:${profile.updatedAt ?? ""}` : null;
  if (profile && profileSyncKey !== syncedProfileKey) {
    setSyncedProfileKey(profileSyncKey);
    setFullName(profile.fullName ?? "");
    setPhoneNumber(profile.phoneNumber ?? "");
    setAvatarKey(profile.avatarKey ?? "");
    setAvatarPreviewUrl(profile.avatarUrl ?? null);

    const prefs = profile.stylePreferences;
    setFavoriteFormats(prefs?.favoriteFormats ?? []);
    setFavoriteProcesses(prefs?.favoriteProcesses ?? []);
    setPreferredColorTones(prefs?.preferredColorTones ?? []);
    setFavoriteCamerasInput((prefs?.favoriteCameras ?? []).join(", "));
    setShootingLevel(prefs?.shootingLevel ?? "INTERMEDIATE");
  }

  const updateMutation = useUpdateProfileMutation(() => {
    setErrorMessage(null);
    setSuccessMessage("Cập nhật hồ sơ cá nhân và sở thích nhiếp ảnh thành công.");
  });

  const uploadAvatarMutation = useUploadAvatarMutation((updated) => {
    setAvatarKey(updated.avatarKey ?? "");
    if (updated.avatarUrl) {
      setAvatarPreviewUrl(updated.avatarUrl);
    }
    setErrorMessage(null);
    setSuccessMessage("Cập nhật ảnh đại diện thành công.");
  });

  const handleAvatarFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const allowedTypes = ["image/jpeg", "image/png", "image/webp"];
    if (!allowedTypes.includes(file.type)) {
      setErrorMessage("Chỉ hỗ trợ ảnh đại diện định dạng JPG, PNG hoặc WebP.");
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setErrorMessage("Dung lượng ảnh gốc không được vượt quá 10MB.");
      return;
    }

    setErrorMessage(null);
    setSuccessMessage(null);
    setPendingAvatarFile(file);
    setIsCropModalOpen(true);
    e.target.value = "";
  };

  const handleConfirmAvatarCrop = (croppedFile: File, previewUrl: string) => {
    if (previewUrl) {
      setAvatarPreviewUrl(previewUrl);
    }
    setIsCropModalOpen(false);
    setPendingAvatarFile(null);
    uploadAvatarMutation.mutate(croppedFile, {
      onError: (err) => setErrorMessage(parseApiError(err)),
    });
  };

  const toggleSelection = (
    current: string[],
    code: string,
    setter: (next: string[]) => void
  ) => {
    if (current.includes(code)) {
      setter(current.filter((item) => item !== code));
    } else {
      setter([...current, code]);
    }
  };

  const cameraTags = favoriteCamerasInput
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);

  const handleRemoveCameraChip = (camToRemove: string) => {
    const next = cameraTags.filter((c) => c !== camToRemove).join(", ");
    setFavoriteCamerasInput(next);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setSuccessMessage(null);

    const parsed = updateProfileSchema.safeParse({
      fullName,
      phoneNumber,
      avatarKey: avatarKey.trim() || undefined,
      shootingLevel,
      favoriteCamerasInput,
    });

    if (!parsed.success) {
      setErrorMessage(parsed.error.issues[0]?.message || "Thông tin hồ sơ không hợp lệ.");
      return;
    }

    const favoriteCameras = favoriteCamerasInput
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean);

    updateMutation.mutate(
      {
        fullName: parsed.data.fullName,
        phoneNumber: parsed.data.phoneNumber,
        avatarKey: parsed.data.avatarKey || undefined,
        stylePreferences: {
          favoriteFormats,
          favoriteProcesses,
          preferredColorTones,
          favoriteCameras,
          shootingLevel,
        },
      },
      {
        onError: (err) => setErrorMessage(parseApiError(err)),
      }
    );
  };

  if (isLoading && !profile) {
    return (
      <div className="p-6 bg-white border border-[#E6E4E0] rounded-2xl text-xs text-stone-500 shadow-xs">
        Đang tải thông tin hồ sơ cá nhân...
      </div>
    );
  }

  const filteredApplications =
    roleStatusFilter === "ALL"
      ? roleApplications
      : roleApplications.filter((app) => app.status === roleStatusFilter);

  return (
    <div className="space-y-7">
      {/* 1. BANNER TỔNG QUAN HỒ SƠ (Matching Stitch Profile Overview Card) */}
      <section className="bg-white rounded-2xl border border-[#E6E4E0] p-6 lg:p-7 shadow-xs">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
          {/* Left + Center: Avatar & User Identifiers */}
          <div className="flex flex-col sm:flex-row items-start sm:items-center gap-6">
            <div className="relative group shrink-0">
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={uploadAvatarMutation.isPending}
                aria-label="Cập nhật ảnh đại diện"
                title="Nhấn để đổi ảnh đại diện"
                className="relative w-24 h-24 rounded-full overflow-hidden border-2 border-[#D97706] ring-4 ring-amber-50 bg-stone-100 flex items-center justify-center focus:outline-none focus:ring-2 focus:ring-[#D97706] transition-transform active:scale-95 disabled:opacity-60 cursor-pointer shadow-sm"
              >
                {avatarPreviewUrl || profile?.avatarUrl ? (
                  <img
                    src={avatarPreviewUrl || profile?.avatarUrl || ""}
                    alt={profile?.fullName || fullName}
                    data-testid="user-avatar-img"
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <UserIcon className="w-9 h-9 text-[#D97706]" />
                )}
                <span className="absolute inset-0 bg-black/45 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                  <Camera className="w-6 h-6 text-white" />
                </span>
              </button>
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={uploadAvatarMutation.isPending}
                aria-label="Chọn ảnh đại diện mới"
                className="absolute -bottom-1 -right-1 w-8 h-8 rounded-full bg-[#D97706] hover:bg-[#B45309] text-white border-2 border-white flex items-center justify-center shadow-md hover:scale-105 transition-transform cursor-pointer"
              >
                <Camera className="w-4 h-4" />
              </button>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                onChange={handleAvatarFileChange}
                data-testid="avatar-file-input"
                className="hidden"
              />
            </div>

            <div className="space-y-2">
              <div className="flex flex-wrap items-center gap-2.5">
                <h2 className="text-2xl font-extrabold tracking-tight text-[#111111]">
                  {profile?.fullName || "Hồ Sơ Nhiếp Ảnh Gia"}
                </h2>
                <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-bold bg-[#ECFDF5] text-[#047857] border border-[#A7F3D0]">
                  <span className="w-1.5 h-1.5 rounded-full bg-[#10B981] animate-pulse" />
                  {profile?.accountStatus || "ACTIVE"}
                </span>
                {(profile?.roles ?? ["CUSTOMER"]).map((role) => (
                  <Badge key={role} variant="amber" size="sm">
                    {role}
                  </Badge>
                ))}
                {profile?.authProvider && (
                  <Badge variant="neutral" size="sm">
                    {profile.authProvider === "GOOGLE" ? "Đã liên kết Google" : profile.authProvider}
                  </Badge>
                )}
              </div>

              <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-stone-600 font-medium">
                <span className="inline-flex items-center gap-1.5 font-mono">
                  <Mail className="w-3.5 h-3.5 text-stone-400" />
                  <span>{profile?.email || "nhiepanh@analoghub.vn"}</span>
                  <span className="text-[10px] bg-stone-100 text-stone-500 px-1.5 py-0.5 rounded font-mono">
                    Read-only
                  </span>
                </span>
                {phoneNumber && (
                  <>
                    <span>•</span>
                    <span className="inline-flex items-center gap-1">
                      <Phone className="w-3.5 h-3.5 text-stone-400" />
                      <span>{phoneNumber}</span>
                    </span>
                  </>
                )}
              </div>

              <p className="text-[11px] text-stone-400 italic">
                Hỗ trợ JPG/PNG/WEBP • Tối đa 10MB (POST /api/v1/users/me/avatar)
              </p>
            </div>
          </div>

          {/* Right: Action Buttons */}
          <div className="flex flex-wrap items-center gap-2.5 self-start md:self-center">
            <Button
              type="button"
              variant="outline"
              size="sm"
              leftIcon={<Award className="w-4 h-4 text-[#D97706]" />}
              onClick={() => setIsRoleModalOpen(true)}
              className="border-[#D97706]/70 text-[#B45309] bg-[#FFF7ED] hover:bg-amber-100/70"
            >
              Nộp đơn nâng cấp vai trò
            </Button>

            <button
              type="button"
              onClick={() => logoutMutation.mutate({ logoutAllDevices: false })}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-bold text-stone-700 bg-stone-100 hover:bg-stone-200 transition-colors cursor-pointer"
            >
              <LogOut className="w-3.5 h-3.5 text-stone-600" />
              <span>Đăng xuất thiết bị này</span>
            </button>

            <button
              type="button"
              onClick={() => logoutMutation.mutate({ logoutAllDevices: true })}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-bold text-[#E11D48] bg-[#FFF1F2] border border-[#FECDD3] hover:bg-rose-100 transition-colors cursor-pointer"
              title="Thu hồi mọi phiên đăng nhập trên tất cả thiết bị"
            >
              <Power className="w-3.5 h-3.5" />
              <span>Thu hồi mọi phiên đăng nhập</span>
            </button>
          </div>
        </div>
      </section>

      {/* 2. KHỐI CẬP NHẬT HỒ SƠ & GU CHỤP PHIM (StylePreferencesDto) */}
      <section className="bg-white rounded-2xl border border-[#E6E4E0] p-6 lg:p-7 shadow-xs space-y-6">
        <form onSubmit={handleSubmit} className="space-y-6" noValidate>
          <div className="flex flex-wrap items-center justify-between gap-4 pb-5 border-b border-stone-100">
            <div>
              <h3 className="text-lg font-extrabold text-[#111111] tracking-tight">
                Thông tin cá nhân &amp; Gu nhiếp ảnh phim
              </h3>
              <p className="text-xs text-stone-500 mt-0.5">
                Cập nhật hồ sơ hiển thị trên cộng đồng và thiết lập gu tráng scan phim phù hợp
              </p>
            </div>

            <Button
              type="submit"
              variant="primary"
              size="sm"
              isLoading={updateMutation.isPending}
              rightIcon={
                <span
                  aria-hidden="true"
                  className="w-5 h-5 rounded-full bg-[#F59E0B] text-[#111111] flex items-center justify-center font-bold text-xs"
                >
                  <Check className="w-3 h-3 stroke-[3]" />
                </span>
              }
            >
              Lưu hồ sơ
            </Button>
          </div>

          {errorMessage && (
            <div
              role="alert"
              className="p-3.5 rounded-xl bg-[#FFF1F2] border border-[#FECDD3] text-xs font-medium text-[#BE123C] flex items-start gap-2"
            >
              <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5 text-[#E11D48]" />
              <span>{errorMessage}</span>
            </div>
          )}

          {successMessage && (
            <div
              role="status"
              className="p-3.5 rounded-xl bg-[#ECFDF5] border border-[#A7F3D0] text-xs font-medium text-[#065F46] flex items-center gap-2"
            >
              <CheckCircle2 className="w-4 h-4 text-[#059669] shrink-0" />
              <span>{successMessage}</span>
            </div>
          )}

          {/* Họ tên & SĐT */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              label="Họ và tên"
              required
              placeholder="Nguyễn Quang Minh"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
            />
            <Input
              label="Số điện thoại"
              required
              placeholder="0908123456"
              value={phoneNumber}
              onChange={(e) => setPhoneNumber(e.target.value)}
            />
          </div>

          {/* Khung Gu chụp phim (StylePreferencesDto) nền kem ấm #FFFDF9 */}
          <div className="bg-[#FFFDF9] border border-amber-200/90 rounded-2xl p-5 space-y-5 shadow-2xs">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-[#F59E0B]" />
                <h4 className="text-sm font-bold text-[#111111]">
                  Sở thích chụp phim (StylePreferencesDto)
                </h4>
              </div>
              <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-100/70 text-amber-800 border border-amber-300 flex items-center gap-1">
                ✨ Tối ưu gợi ý Film Lab
              </span>
            </div>

            {/* 1. Khổ phim yêu thích (favoriteFormats) */}
            <div className="space-y-2">
              <span className="block text-xs font-semibold text-stone-700">
                1. Khổ phim yêu thích (favoriteFormats):
              </span>
              <div className="flex flex-wrap gap-2">
                {FILM_FORMAT_OPTIONS.map((fmt) => {
                  const active = favoriteFormats.includes(fmt.code);
                  return (
                    <button
                      key={fmt.code}
                      type="button"
                      onClick={() =>
                        toggleSelection(favoriteFormats, fmt.code, setFavoriteFormats)
                      }
                      className={cn(
                        "px-3.5 py-1.5 rounded-xl text-xs transition-all flex items-center gap-1.5 cursor-pointer",
                        active
                          ? "bg-amber-50 text-amber-900 border-2 border-[#D97706] font-bold shadow-2xs"
                          : "bg-white border border-stone-200 text-stone-600 hover:border-stone-300 font-medium"
                      )}
                    >
                      {active && <CheckCircle2 className="w-3.5 h-3.5 text-[#D97706]" />}
                      <span>{fmt.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* 2. Quy trình tráng rửa yêu thích (favoriteProcesses) */}
            <div className="space-y-2">
              <span className="block text-xs font-semibold text-stone-700">
                2. Quy trình tráng thường dùng (favoriteProcesses):
              </span>
              <div className="flex flex-wrap gap-2">
                {CHEMICAL_PROCESS_OPTIONS.map((proc) => {
                  const active = favoriteProcesses.includes(proc.code);
                  return (
                    <button
                      key={proc.code}
                      type="button"
                      onClick={() =>
                        toggleSelection(favoriteProcesses, proc.code, setFavoriteProcesses)
                      }
                      className={cn(
                        "px-3.5 py-1.5 rounded-xl text-xs transition-all flex items-center gap-2 cursor-pointer",
                        active
                          ? "bg-white border-2 border-[#D97706] text-[#111111] font-bold shadow-2xs"
                          : "bg-white border border-stone-200 text-stone-600 hover:border-stone-300 font-medium"
                      )}
                    >
                      <span
                        className={cn(
                          "w-2.5 h-2.5 rounded-full",
                          PROCESS_DOT_COLORS[proc.code] || "bg-[#F59E0B]"
                        )}
                      />
                      <span>{proc.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* 3. Tone màu đặc trưng (preferredColorTones) */}
            <div className="space-y-2">
              <span className="block text-xs font-semibold text-stone-700">
                3. Tone màu đặc trưng (preferredColorTones):
              </span>
              <div className="flex flex-wrap gap-2">
                {COLOR_TONE_OPTIONS.map((tone) => {
                  const active = preferredColorTones.includes(tone.code);
                  return (
                    <button
                      key={tone.code}
                      type="button"
                      onClick={() =>
                        toggleSelection(
                          preferredColorTones,
                          tone.code,
                          setPreferredColorTones
                        )
                      }
                      className={cn(
                        "px-3 py-1.5 rounded-xl text-xs transition-all cursor-pointer",
                        active
                          ? "bg-amber-50 text-amber-900 border-2 border-[#D97706] font-bold shadow-2xs"
                          : "bg-white border border-stone-200 text-stone-600 hover:border-stone-300 font-medium"
                      )}
                    >
                      {tone.label}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* 4. Trình độ chụp phim & 5. Máy ảnh yêu thích */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-1">
              <div className="space-y-1.5">
                <label
                  htmlFor="profile-shooting-level"
                  className="block text-xs font-bold text-[#111111] uppercase tracking-wider"
                >
                  Trình độ chụp phim
                </label>
                <select
                  id="profile-shooting-level"
                  value={shootingLevel}
                  onChange={(e) => setShootingLevel(e.target.value)}
                  className="w-full h-11 rounded-xl bg-white border border-stone-300 px-3.5 text-sm font-medium text-[#111111] focus:outline-none focus:border-[#D97706] focus:ring-4 focus:ring-amber-100/80"
                >
                  {SHOOTING_LEVEL_OPTIONS.map((lvl) => (
                    <option key={lvl.value} value={lvl.value}>
                      {lvl.label}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-2">
                <Input
                  label="Máy ảnh yêu thích"
                  placeholder="Leica M6, Hasselblad 500C/M"
                  value={favoriteCamerasInput}
                  onChange={(e) => setFavoriteCamerasInput(e.target.value)}
                  leftIcon={<Camera className="w-4 h-4" />}
                />
                {cameraTags.length > 0 && (
                  <div className="flex flex-wrap gap-1.5">
                    {cameraTags.map((cam) => (
                      <span
                        key={cam}
                        className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-md text-xs font-bold bg-amber-50 text-amber-900 border border-amber-300"
                      >
                        <span>{cam}</span>
                        <button
                          type="button"
                          onClick={() => handleRemoveCameraChip(cam)}
                          className="hover:text-[#E11D48] cursor-pointer"
                          aria-label={`Bỏ máy ${cam}`}
                        >
                          <X className="w-3 h-3" />
                        </button>
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        </form>

        {/* 3. LỊCH SỬ HỒ SƠ NÂNG CẤP VAI TRÒ (Matching Stitch Role Application History) */}
        {roleApplications.length > 0 && (
          <div className="pt-5 border-t border-stone-100 space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h4 className="text-xs font-bold uppercase tracking-wider text-stone-700 flex items-center gap-1.5">
                <Sparkles className="w-4 h-4 text-[#D97706]" />
                <span>Trạng Thái Hồ Sơ Xin Nâng Cấp Vai Trò ({roleApplications.length})</span>
              </h4>

              <div className="flex items-center gap-1 p-1 bg-stone-100 rounded-lg text-[11px] font-semibold">
                {(["ALL", "PENDING", "APPROVED", "REJECTED"] as const).map((st) => (
                  <button
                    key={st}
                    type="button"
                    onClick={() => setRoleStatusFilter(st)}
                    className={cn(
                      "px-2.5 py-1 rounded-md transition-colors cursor-pointer",
                      roleStatusFilter === st
                        ? "bg-[#111111] text-white"
                        : "text-stone-600 hover:text-[#111111]"
                    )}
                  >
                    {st === "ALL" ? "Tất cả" : st}
                  </button>
                ))}
              </div>
            </div>

            <div className="space-y-2.5">
              {filteredApplications.map((app) => {
                const borderLeftClass =
                  app.status === "APPROVED"
                    ? "border-l-4 border-l-[#059669]"
                    : app.status === "REJECTED"
                      ? "border-l-4 border-l-[#E11D48]"
                      : "border-l-4 border-l-[#D97706]";

                return (
                  <div
                    key={app.id}
                    className={cn(
                      "p-4 rounded-xl bg-[#FAF8F5] border border-stone-200 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs",
                      borderLeftClass
                    )}
                  >
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="font-extrabold text-[#111111]">{app.requestedRole}</span>
                        <span className="font-mono text-[11px] text-stone-400">#{app.id}</span>
                      </div>
                      {app.reviewNote ? (
                        <p className="text-stone-600">{app.reviewNote}</p>
                      ) : (
                        <p className="text-stone-500 inline-flex items-center gap-1">
                          <Clock className="w-3 h-3 text-[#D97706]" />
                          <span>Đang trong hàng đợi thẩm định của Ban quản trị (24–48h)</span>
                        </p>
                      )}
                    </div>
                    <Badge
                      variant={
                        app.status === "APPROVED"
                          ? "emerald"
                          : app.status === "REJECTED"
                            ? "red"
                            : "amber"
                      }
                      size="sm"
                    >
                      {app.status}
                    </Badge>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </section>

      <AvatarCropModal
        isOpen={isCropModalOpen}
        file={pendingAvatarFile}
        isUploading={uploadAvatarMutation.isPending}
        onClose={() => {
          setIsCropModalOpen(false);
          setPendingAvatarFile(null);
        }}
        onConfirmCrop={handleConfirmAvatarCrop}
      />

      <RoleApplicationModal
        isOpen={isRoleModalOpen}
        onClose={() => setIsRoleModalOpen(false)}
      />
    </div>
  );
};
