import React, { useState } from "react";
import {
  AlertTriangle,
  ArrowRight,
  Award,
  Building2,
  CheckCircle2,
  Clock,
  ExternalLink,
  FileCheck2,
  Truck,
} from "lucide-react";
import { parseApiError } from "@/shared/api/handleApiError";
import { Button } from "@/shared/components/ui/Button";
import { Input } from "@/shared/components/ui/Input";
import { Modal } from "@/shared/components/ui/Modal";
import { cn } from "@/shared/utils/cn";
import { useSubmitRoleApplicationMutation } from "../hooks/useProfileQueries";
import {
  CHEMICAL_PROCESS_OPTIONS,
  EXPERT_SPECIALTY_OPTIONS,
} from "../schemas/profile.schema";
import type { RoleApplicationDto, RoleUpgradeCode } from "../types/iam.types";

export interface RoleApplicationModalProps {
  isOpen: boolean;
  onClose: () => void;
  defaultRole?: RoleUpgradeCode;
  onSuccess?: (application: RoleApplicationDto) => void;
}

const ROLE_OPTIONS: Array<{
  code: RoleUpgradeCode;
  title: string;
  subtitle: string;
  iconBoxClass: string;
  codeColorClass: string;
  tags: string[];
  icon: React.ComponentType<{ className?: string }>;
}> = [
  {
    code: "EXPERT",
    title: "CHUYÊN GIA KIỂM ĐỊNH",
    subtitle: "Thẩm định chất lượng máy ảnh phim, cơ chế màn trập, thấu kính ký quỹ Escrow.",
    iconBoxClass: "bg-[#ECFDF5] border-[#A7F3D0] text-[#059669]",
    codeColorClass: "text-[#059669]",
    tags: ["specialtyAreas", "Kinh nghiệm ≥ 1 năm"],
    icon: Award,
  },
  {
    code: "LAB_OWNER",
    title: "CHỦ PHÒNG LAB",
    subtitle: "Mở gian hàng Film Lab, nhận đơn đặt tráng scan và công cụ quản lý Kanban.",
    iconBoxClass: "bg-[#FFF7ED] border-[#FDBA74] text-[#D97706]",
    codeColorClass: "text-[#C2410C]",
    tags: ["Tên Lab", "Địa chỉ & GPS", "Thiết bị tráng", "Hóa chất"],
    icon: Building2,
  },
  {
    code: "DELIVERY_PARTNER",
    title: "ĐỐI TÁC GIAO VẬN PHIM",
    subtitle: "Vận chuyển hỏa tốc cuộn phim từ khách hàng tới Film Lab an toàn.",
    iconBoxClass: "bg-[#EEF2FF] border-[#C7D2FE] text-[#4F46E5]",
    codeColorClass: "text-[#4F46E5]",
    tags: ["vehicleType", "licensePlate"],
    icon: Truck,
  },
];

export const RoleApplicationModal: React.FC<RoleApplicationModalProps> = ({
  isOpen,
  onClose,
  defaultRole = "EXPERT",
  onSuccess,
}) => {
  const [selectedRole, setSelectedRole] = useState<RoleUpgradeCode>(defaultRole);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // EXPERT fields
  const [specialtyAreas, setSpecialtyAreas] = useState<string[]>([
    "DARKROOM_PRINTING",
    "C41_CHEMISTRY",
  ]);
  const [yearsOfExperience, setYearsOfExperience] = useState<string>("5");
  const [portfolioLinksInput, setPortfolioLinksInput] = useState<string>("");
  const [sampleArticleSummary, setSampleArticleSummary] = useState<string>("");

  // LAB_OWNER fields
  const [proposedLabName, setProposedLabName] = useState<string>("");
  const [labAddress, setLabAddress] = useState<string>("");
  const [equipmentListInput, setEquipmentListInput] = useState<string>(
    "Noritsu HS-1800, Fujifilm Frontier SP-3000"
  );
  const [supportedProcesses, setSupportedProcesses] = useState<string[]>(["C41", "ECN2", "BW"]);
  const [businessLicenseNo, setBusinessLicenseNo] = useState<string>("");
  const [labLatitude, setLabLatitude] = useState<string>("10.776889");
  const [labLongitude, setLabLongitude] = useState<string>("106.700806");

  // DELIVERY_PARTNER fields
  const [vehicleType, setVehicleType] = useState<string>("Xe máy Honda Winner X 150cc");
  const [licensePlate, setLicensePlate] = useState<string>("");
  const [operatingDistrictsInput, setOperatingDistrictsInput] = useState<string>(
    "Quận 1, Quận 3, Quận Bình Thạnh"
  );

  const submitMutation = useSubmitRoleApplicationMutation((submitted) => {
    setErrorMessage(null);
    setSuccessMessage(
      "Nộp hồ sơ đăng ký nâng cấp vai trò thành công. Ban quản trị sẽ xét duyệt trong vòng 24-48 giờ."
    );
    onSuccess?.(submitted);
  });

  const toggleItem = (list: string[], value: string, setter: (next: string[]) => void) => {
    if (list.includes(value)) {
      setter(list.filter((item) => item !== value));
    } else {
      setter([...list, value]);
    }
  };

  const splitCsv = (raw: string): string[] =>
    raw
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setSuccessMessage(null);

    if (selectedRole === "EXPERT") {
      if (specialtyAreas.length === 0) {
        setErrorMessage("Vui lòng chọn ít nhất 1 lĩnh vực chuyên môn (specialtyAreas).");
        return;
      }
      const parsedYears = Number(yearsOfExperience);
      if (!Number.isFinite(parsedYears) || parsedYears < 1) {
        setErrorMessage("Số năm kinh nghiệm phải từ 1 năm trở lên.");
        return;
      }
      const portfolioLinks = splitCsv(portfolioLinksInput);
      if (portfolioLinks.length === 0) {
        setErrorMessage("Vui lòng đính kèm ít nhất 1 đường dẫn link Portfolio.");
        return;
      }

      submitMutation.mutate(
        {
          requestedRole: "EXPERT",
          portfolioPayload: {
            specialtyAreas,
            yearsOfExperience: parsedYears,
            portfolioLinks,
            sampleArticleSummary: sampleArticleSummary.trim(),
          },
        },
        {
          onError: (err) => setErrorMessage(parseApiError(err)),
        }
      );
      return;
    }

    if (selectedRole === "LAB_OWNER") {
      const equipmentList = splitCsv(equipmentListInput);
      if (!proposedLabName.trim() || !labAddress.trim()) {
        setErrorMessage("Vui lòng nhập đầy đủ tên phòng Lab và địa chỉ phòng Lab.");
        return;
      }
      if (equipmentList.length === 0 || supportedProcesses.length === 0) {
        setErrorMessage("Vui lòng liệt kê thiết bị và quy trình hóa chất hỗ trợ.");
        return;
      }

      submitMutation.mutate(
        {
          requestedRole: "LAB_OWNER",
          portfolioPayload: {
            proposedLabName: proposedLabName.trim(),
            labAddress: labAddress.trim(),
            equipmentList,
            supportedProcesses,
            businessLicenseNo: businessLicenseNo.trim() || undefined,
            latitude: Number(labLatitude) || 10.776889,
            longitude: Number(labLongitude) || 106.700806,
          },
        },
        {
          onError: (err) => setErrorMessage(parseApiError(err)),
        }
      );
      return;
    }

    if (!vehicleType.trim() || !licensePlate.trim()) {
      setErrorMessage("Vui lòng nhập đầy đủ loại phương tiện và biển kiểm soát.");
      return;
    }

    submitMutation.mutate(
      {
        requestedRole: "DELIVERY_PARTNER",
        portfolioPayload: {
          vehicleType: vehicleType.trim(),
          licensePlate: licensePlate.trim(),
          operatingDistricts: splitCsv(operatingDistrictsInput),
        },
      },
      {
        onError: (err) => setErrorMessage(parseApiError(err)),
      }
    );
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      maxWidth="2xl"
      title="Đăng ký nâng cấp vai trò đối tác"
      description="Nộp hồ sơ năng lực chuyên môn để tham gia mạng lưới Film Lab, Chuyên gia thẩm định hoặc Đối tác giao nhận AnalogHub."
    >
      <form onSubmit={handleSubmit} className="space-y-5" noValidate>
        {/* Top SLA Banner */}
        <div className="flex flex-wrap items-center justify-between gap-2 px-3.5 py-2 rounded-xl bg-[#FFF7ED] border border-[#FDBA74] text-xs text-[#9A3412]">
          <span className="inline-flex items-center gap-1.5 font-semibold">
            <Clock className="w-4 h-4 text-[#D97706]" />
            <span>Xét duyệt hồ sơ trong 24–48 giờ</span>
          </span>
          <span className="font-mono text-[11px] bg-white px-2 py-0.5 rounded border border-[#FED7AA] text-[#C2410C]">
            POST /api/v1/role-applications
          </span>
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
            <CheckCircle2 className="w-4 h-4 shrink-0 text-[#059669]" />
            <span>{successMessage}</span>
          </div>
        )}

        {/* 3 Role Picker Cards (RoleCode Picker matching Stitch) */}
        <div className="space-y-2">
          <span className="block text-xs font-bold uppercase tracking-wider text-stone-700">
            Chọn vai trò muốn đăng ký nâng cấp
          </span>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {ROLE_OPTIONS.map((item) => {
              const Icon = item.icon;
              const isSelected = selectedRole === item.code;
              return (
                <button
                  key={item.code}
                  type="button"
                  onClick={() => {
                    setSelectedRole(item.code);
                    setErrorMessage(null);
                  }}
                  className={cn(
                    "p-4 rounded-xl border text-left transition-all flex flex-col justify-between gap-2 cursor-pointer",
                    isSelected
                      ? "bg-[#FFFDF9] border-2 border-[#D97706] shadow-sm"
                      : "bg-white border-stone-200 hover:border-stone-300"
                  )}
                >
                  <div>
                    <div className="flex items-start justify-between">
                      <div
                        className={cn(
                          "w-10 h-10 rounded-lg border flex items-center justify-center",
                          item.iconBoxClass
                        )}
                      >
                        <Icon className="w-5 h-5" />
                      </div>
                      {isSelected && (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-[#FFF7ED] text-[#C2410C] text-[10px] font-bold border border-[#FDBA74]">
                          <span className="w-1.5 h-1.5 rounded-full bg-[#D97706] animate-pulse" />
                          Đang chọn
                        </span>
                      )}
                    </div>
                    <h4 className="text-xs font-extrabold text-[#111111] mt-2.5">{item.title}</h4>
                    <p className={cn("font-mono text-[10px] font-semibold", item.codeColorClass)}>
                      {item.code}
                    </p>
                    <p className="text-[11px] text-stone-500 mt-1 leading-snug">{item.subtitle}</p>
                  </div>

                  <div className="pt-2 border-t border-stone-100 flex flex-wrap gap-1">
                    {item.tags.map((tag) => (
                      <span
                        key={tag}
                        className="px-1.5 py-0.5 rounded text-[10px] font-medium bg-stone-50 border border-stone-200 text-stone-600"
                      >
                        {tag}
                      </span>
                    ))}
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        {/* Dynamic Portfolio Payload Form */}
        {selectedRole === "EXPERT" && (
          <div className="space-y-4 pt-2 border-t border-stone-100">
            <div className="space-y-2">
              <span className="block text-xs font-bold uppercase tracking-wider text-stone-700">
                Lĩnh vực chuyên môn (specialtyAreas) <span className="text-[#E11D48]">*</span>
              </span>
              <div className="flex flex-wrap gap-2">
                {EXPERT_SPECIALTY_OPTIONS.map((opt) => {
                  const active = specialtyAreas.includes(opt.code);
                  return (
                    <button
                      key={opt.code}
                      type="button"
                      onClick={() => toggleItem(specialtyAreas, opt.code, setSpecialtyAreas)}
                      className={cn(
                        "px-3 py-1.5 rounded-xl text-xs font-semibold border transition-colors cursor-pointer",
                        active
                          ? "bg-[#FFF7ED] border-2 border-[#D97706] text-[#9A3412]"
                          : "bg-white border-stone-200 text-stone-600 hover:border-stone-300"
                      )}
                    >
                      {opt.label}
                    </button>
                  );
                })}
              </div>
            </div>

            <Input
              label="Số năm kinh nghiệm"
              required
              type="number"
              min={1}
              max={60}
              value={yearsOfExperience}
              onChange={(e) => setYearsOfExperience(e.target.value)}
              helperText="Số năm thực hành chụp phim, tráng rọi hoặc kiểm định máy cơ."
            />

            <Input
              label="Link Portfolio (cách nhau bằng dấu phẩy)"
              required
              placeholder="https://flickr.com/photos/ten-ban, https://behance.net/ten-ban"
              value={portfolioLinksInput}
              onChange={(e) => setPortfolioLinksInput(e.target.value)}
              leftIcon={<ExternalLink className="w-4 h-4" />}
              helperText="Đính kèm link Flickr, Behance, Instagram hoặc Website cá nhân để Admin thẩm định."
            />

            <div className="w-full space-y-1.5">
              <label
                htmlFor="expert-sample-summary"
                className="block text-xs font-bold text-[#111111] uppercase tracking-wider"
              >
                Tóm tắt bài viết mẫu / Kinh nghiệm chuyên môn
              </label>
              <textarea
                id="expert-sample-summary"
                rows={3}
                value={sampleArticleSummary}
                onChange={(e) => setSampleArticleSummary(e.target.value)}
                placeholder="Chia sẻ ngắn gọn kinh nghiệm kiểm soát hóa chất C-41/ECN-2, rọi ảnh hoặc thẩm định máy cơ..."
                className="w-full rounded-xl bg-white border border-stone-300 px-3.5 py-2.5 text-sm text-[#111111] placeholder:text-stone-400 focus:outline-none focus:border-[#D97706] focus:ring-4 focus:ring-amber-100/80"
              />
            </div>
          </div>
        )}

        {selectedRole === "LAB_OWNER" && (
          <div className="space-y-4 pt-2 border-t border-stone-100">
            <Input
              label="Tên phòng Lab đề xuất (proposedLabName)"
              required
              placeholder="Ví dụ: Saigon Silver Halide Film Lab"
              value={proposedLabName}
              onChange={(e) => setProposedLabName(e.target.value)}
            />

            <Input
              label="Địa chỉ phòng Lab (labAddress)"
              required
              placeholder="Số 42 Trần Quốc Thảo, Phường Võ Thị Sáu, Quận 3, TP. Hồ Chí Minh"
              value={labAddress}
              onChange={(e) => setLabAddress(e.target.value)}
            />

            <Input
              label="Danh sách thiết bị tráng/scan (equipmentList)"
              required
              placeholder="Noritsu HS-1800, Fujifilm Frontier SP-3000, Jobo CPP-3"
              value={equipmentListInput}
              onChange={(e) => setEquipmentListInput(e.target.value)}
            />

            <div className="space-y-2">
              <span className="block text-xs font-bold uppercase tracking-wider text-stone-700">
                Quy trình hóa chất hỗ trợ (supportedProcesses){" "}
                <span className="text-[#E11D48]">*</span>
              </span>
              <div className="flex flex-wrap gap-2">
                {CHEMICAL_PROCESS_OPTIONS.map((proc) => {
                  const active = supportedProcesses.includes(proc.code);
                  return (
                    <button
                      key={proc.code}
                      type="button"
                      onClick={() =>
                        toggleItem(supportedProcesses, proc.code, setSupportedProcesses)
                      }
                      className={cn(
                        "px-3 py-1.5 rounded-xl text-xs font-semibold border transition-colors cursor-pointer",
                        active
                          ? "bg-[#FFF7ED] border-2 border-[#D97706] text-[#9A3412]"
                          : "bg-white border-stone-200 text-stone-600 hover:border-stone-300"
                      )}
                    >
                      {proc.label}
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <Input
                label="Mã số GPKD (Tùy chọn)"
                placeholder="0316891234"
                value={businessLicenseNo}
                onChange={(e) => setBusinessLicenseNo(e.target.value)}
              />
              <Input
                label="Vĩ độ Lab (Latitude)"
                type="number"
                step="0.000001"
                value={labLatitude}
                onChange={(e) => setLabLatitude(e.target.value)}
              />
              <Input
                label="Kinh độ Lab (Longitude)"
                type="number"
                step="0.000001"
                value={labLongitude}
                onChange={(e) => setLabLongitude(e.target.value)}
              />
            </div>
          </div>
        )}

        {selectedRole === "DELIVERY_PARTNER" && (
          <div className="space-y-4 pt-2 border-t border-stone-100">
            <Input
              label="Loại phương tiện (vehicleType)"
              required
              placeholder="Ví dụ: Xe máy Honda Winner X 150cc"
              value={vehicleType}
              onChange={(e) => setVehicleType(e.target.value)}
            />
            <Input
              label="Biển kiểm soát (licensePlate)"
              required
              placeholder="Ví dụ: 59-T2 688.88"
              value={licensePlate}
              onChange={(e) => setLicensePlate(e.target.value)}
            />
            <Input
              label="Khu vực hoạt động (cách nhau bằng dấu phẩy)"
              placeholder="Quận 1, Quận 3, Quận Bình Thạnh"
              value={operatingDistrictsInput}
              onChange={(e) => setOperatingDistrictsInput(e.target.value)}
            />
          </div>
        )}

        {/* S3 Security Evidence Callout */}
        <div className="p-3 rounded-xl bg-[#FAF8F5] border border-stone-200 flex items-center justify-between text-xs text-stone-600">
          <span className="inline-flex items-center gap-2 font-medium">
            <FileCheck2 className="w-4 h-4 text-[#059669]" />
            <span>Hồ sơ được chuẩn hóa theo RoleApplicationPayloadValidator</span>
          </span>
          <span className="font-mono text-[10px] text-stone-400">AES-256 S3 Verified</span>
        </div>

        <div className="flex items-center justify-end gap-3 pt-3 border-t border-stone-200">
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy bỏ
          </Button>
          <Button
            type="submit"
            variant="primary"
            isLoading={submitMutation.isPending}
            rightIcon={
              <span
                aria-hidden="true"
                className="w-5 h-5 rounded-full bg-[#F59E0B] text-[#111111] flex items-center justify-center"
              >
                <ArrowRight className="w-3 h-3 stroke-[2.5]" />
              </span>
            }
          >
            Gửi hồ sơ xét duyệt
          </Button>
        </div>
      </form>
    </Modal>
  );
};
