import React, { useState } from "react";
import {
  AlertTriangle,
  ArrowRight,
  Check,
  CheckCircle2,
  Eye,
  EyeOff,
  KeyRound,
  Laptop,
  Loader2,
  ShieldAlert,
  ShieldCheck,
} from "lucide-react";
import { parseApiError } from "@/shared/api/handleApiError";
import { Input } from "@/shared/components/ui/Input";
import { cn } from "@/shared/utils/cn";
import { useChangePasswordMutation } from "../hooks/useAuthMutations";
import {
  changePasswordSchema,
  evaluatePasswordStrength,
} from "../schemas/auth.schema";
import { getBrowserDeviceInfo } from "../utils/authRedirect";

export const ChangePasswordCard: React.FC = () => {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmNewPassword, setConfirmNewPassword] = useState("");
  const [showPasswords, setShowPasswords] = useState(false);

  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const criteria = evaluatePasswordStrength(newPassword);
  const passedCount = [
    criteria.lengthValid,
    criteria.caseValid,
    criteria.digitValid,
    criteria.specialValid,
  ].filter(Boolean).length;

  const changePasswordMutation = useChangePasswordMutation((res) => {
    setErrorMessage(null);
    const revokedCount = res?.revokedSessionsCount ?? 0;
    setSuccessMessage(
      revokedCount > 0
        ? `Đổi mật khẩu thành công! Đã tự động thu hồi ${revokedCount} phiên đăng nhập cũ.`
        : "Đổi mật khẩu thành công! Phiên bảo mật của bạn đã được cập nhật."
    );
    setCurrentPassword("");
    setNewPassword("");
    setConfirmNewPassword("");
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setSuccessMessage(null);

    const parsed = changePasswordSchema.safeParse({
      currentPassword,
      newPassword,
      confirmNewPassword,
    });

    if (!parsed.success) {
      setErrorMessage(parsed.error.issues[0]?.message || "Thông tin mật khẩu không hợp lệ.");
      return;
    }

    changePasswordMutation.mutate(
      {
        currentPassword: parsed.data.currentPassword,
        newPassword: parsed.data.newPassword,
        confirmNewPassword: parsed.data.confirmNewPassword,
      },
      {
        onError: (err) => setErrorMessage(parseApiError(err)),
      }
    );
  };

  const passwordRules = [
    { label: "8–72 ký tự", met: criteria.lengthValid },
    { label: "Chữ hoa & thường", met: criteria.caseValid },
    { label: "Có chữ số (0-9)", met: criteria.digitValid },
    { label: "Ký tự đặc biệt (@$!%*?&_#-)", met: criteria.specialValid },
  ];

  const deviceLabel = getBrowserDeviceInfo() || "Chrome trên Windows 11";

  return (
    <div className="space-y-7">
      {/* ĐỔI MẬT KHẨU & BẢO MẬT PHIÊN (POST /api/v1/auth/change-password) */}
      <section className="bg-white rounded-2xl border border-[#E6E4E0] p-6 lg:p-7 shadow-xs space-y-5">
        <div className="pb-4 border-b border-stone-100">
          <div className="flex items-center gap-2">
            <KeyRound className="w-5 h-5 text-[#D97706]" />
            <h2 className="text-lg font-extrabold text-[#111111] tracking-tight">
              Đổi mật khẩu &amp; Bảo mật
            </h2>
          </div>
          <p className="text-xs text-stone-500 mt-1">
            Quản trị phiên xác thực và chính sách mật khẩu mạnh
          </p>
        </div>

        {/* Banner cảnh báo an toàn */}
        <div className="bg-amber-50 border border-amber-200 rounded-xl p-3.5 flex items-start gap-2.5">
          <ShieldAlert className="w-4 h-4 text-[#D97706] shrink-0 mt-0.5" />
          <p className="text-xs text-amber-900 leading-relaxed font-medium">
            <span className="font-bold">Lưu ý an toàn:</span> Khi đổi mật khẩu thành công, toàn
            bộ các phiên đăng nhập cũ trên thiết bị khác sẽ tự động bị thu hồi theo tiêu chuẩn bảo
            mật phiên AnalogHub.
          </p>
        </div>

        {errorMessage && (
          <div
            role="alert"
            className="p-3 rounded-xl bg-[#FFF1F2] border border-[#FECDD3] text-xs font-medium text-[#BE123C] flex items-start gap-2"
          >
            <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5 text-[#E11D48]" />
            <span>{errorMessage}</span>
          </div>
        )}

        {successMessage && (
          <div
            role="status"
            className="p-3 rounded-xl bg-[#ECFDF5] border border-[#A7F3D0] text-xs font-medium text-[#065F46] flex items-center gap-2"
          >
            <CheckCircle2 className="w-4 h-4 text-[#059669] shrink-0" />
            <span>{successMessage}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate className="space-y-4">
          <Input
            type={showPasswords ? "text" : "password"}
            label="Mật khẩu hiện tại"
            required
            placeholder="Nhập mật khẩu hiện tại"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            rightIcon={
              <button
                type="button"
                tabIndex={-1}
                aria-label={showPasswords ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                onClick={() => setShowPasswords((prev) => !prev)}
                className="text-stone-400 hover:text-stone-700 cursor-pointer"
              >
                {showPasswords ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            }
          />

          <Input
            type={showPasswords ? "text" : "password"}
            label="Mật khẩu mới"
            required
            labelRight={
              <span className="text-[10px] text-stone-400 font-mono">8 – 72 ký tự</span>
            }
            placeholder="Nhập mật khẩu mới chuẩn mạnh"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
          />

          <Input
            type={showPasswords ? "text" : "password"}
            label="Xác nhận mật khẩu mới"
            required
            placeholder="Nhập lại mật khẩu mới"
            value={confirmNewPassword}
            onChange={(e) => setConfirmNewPassword(e.target.value)}
          />

          {/* 4 Tag kiểm tra độ mạnh mật khẩu (Emerald Green) */}
          <div className="pt-1">
            <span className="block text-[11px] font-bold uppercase tracking-wider text-stone-500 mb-2">
              Độ mạnh mật khẩu (Đạt {passedCount}/4 tiêu chuẩn)
            </span>
            <div className="grid grid-cols-2 gap-2">
              {passwordRules.map((rule) => (
                <span
                  key={rule.label}
                  className={cn(
                    "inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-bold border transition-colors",
                    rule.met
                      ? "bg-[#ECFDF5] text-[#065F46] border-[#A7F3D0]"
                      : "bg-stone-50 text-stone-500 border-stone-200"
                  )}
                >
                  <span
                    className={cn(
                      "w-4 h-4 rounded-full flex items-center justify-center text-[10px] shrink-0",
                      rule.met
                        ? "bg-[#059669] text-white"
                        : "bg-stone-200 text-stone-500"
                    )}
                  >
                    <Check className="w-2.5 h-2.5 stroke-[3]" />
                  </span>
                  <span>{rule.label}</span>
                </span>
              ))}
            </div>
          </div>

          <button
            type="submit"
            disabled={changePasswordMutation.isPending}
            className="w-full mt-2 py-3 px-4 bg-[#111111] hover:bg-stone-800 text-white rounded-xl text-xs font-bold flex items-center justify-center gap-2 shadow-sm transition-all active:scale-[0.99] cursor-pointer disabled:opacity-60"
          >
            {changePasswordMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
            <span>Cập nhật mật khẩu &amp; Thu hồi phiên cũ</span>
            <ArrowRight className="w-4 h-4" />
          </button>
        </form>

        {/* Khối thiết bị đang hoạt động */}
        <div className="pt-4 border-t border-stone-100">
          <span className="block text-xs font-bold text-stone-800 mb-2.5">
            Phiên thiết bị đang hoạt động
          </span>

          <div className="p-3 rounded-xl bg-stone-50 border border-stone-200 flex items-center justify-between gap-3">
            <div className="flex items-center gap-3 min-w-0">
              <div className="p-2 rounded-lg bg-white border border-stone-200 text-stone-600 shrink-0">
                <Laptop className="w-5 h-5" />
              </div>
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-[#111111] truncate">
                    Thiết bị hiện tại
                  </span>
                  <span className="px-1.5 py-0.5 text-[10px] font-bold bg-[#ECFDF5] text-[#065F46] border border-[#A7F3D0] rounded shrink-0">
                    Phiên này
                  </span>
                </div>
                <p className="text-[11px] text-stone-400 truncate mt-0.5" title={deviceLabel}>
                  {deviceLabel}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-1.5 text-[11px] font-bold text-[#059669] shrink-0">
              <span className="w-2 h-2 rounded-full bg-[#10B981] animate-pulse" />
              <span>Hoạt động</span>
            </div>
          </div>
        </div>
      </section>

      {/* Thẻ trợ giúp / Quy định Escrow an toàn */}
      <div className="bg-gradient-to-br from-amber-50 to-orange-50 border border-amber-200 rounded-2xl p-5 space-y-2">
        <div className="flex items-center gap-2 text-amber-900 font-bold text-xs">
          <ShieldCheck className="w-4 h-4 text-[#D97706]" />
          <span>Quy tắc bảo mật giao dịch &amp; Ký quỹ Escrow</span>
        </div>
        <p className="text-[11px] text-amber-800/90 leading-relaxed">
          Mọi cuộn phim gửi qua Film Lab và đơn mua máy ảnh trên AnalogHub đều được liên kết mã QR
          đối soát và giữ tiền an toàn 72 giờ để bạn kiểm tra máy trước khi thanh toán cho người
          bán.
        </p>
      </div>
    </div>
  );
};
