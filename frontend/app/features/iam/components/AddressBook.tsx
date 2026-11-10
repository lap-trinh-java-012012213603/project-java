import React, { useState } from "react";
import {
  AlertTriangle,
  Compass,
  Edit3,
  MapPin,
  Navigation,
  Phone,
  Plus,
  Star,
  Trash2,
} from "lucide-react";
import { parseApiError } from "@/shared/api/handleApiError";
import { Badge } from "@/shared/components/ui/Badge";
import { Button } from "@/shared/components/ui/Button";
import { Input } from "@/shared/components/ui/Input";
import { cn } from "@/shared/utils/cn";
import {
  useCreateAddressMutation,
  useDeleteAddressMutation,
  useMyAddressesQuery,
  useUpdateAddressMutation,
} from "../hooks/useProfileQueries";
import {
  DISTRICT_PRESETS,
  PROVINCE_PRESETS,
  upsertAddressSchema,
} from "../schemas/profile.schema";
import type { UpsertUserAddressRequest, UserAddressDto } from "../types/iam.types";

const MAX_ADDRESSES = 10;

export const AddressBook: React.FC = () => {
  const { data: addresses = [], isLoading } = useMyAddressesQuery();
  const createMutation = useCreateAddressMutation(() => resetForm());
  const updateMutation = useUpdateAddressMutation(() => resetForm());
  const deleteMutation = useDeleteAddressMutation();

  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingAddressId, setEditingAddressId] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [recipientName, setRecipientName] = useState("");
  const [recipientPhone, setRecipientPhone] = useState("");
  const [provinceCode, setProvinceCode] = useState("79");
  const [districtCode, setDistrictCode] = useState("760");
  const [wardCode, setWardCode] = useState("26734");
  const [fullAddress, setFullAddress] = useState("");
  const [latitudeInput, setLatitudeInput] = useState("10.775658");
  const [longitudeInput, setLongitudeInput] = useState("106.700424");
  const [isDefault, setIsDefault] = useState(false);

  const availableDistricts = DISTRICT_PRESETS.filter(
    (d) => d.provinceCode === provinceCode
  );

  function resetForm() {
    setIsFormOpen(false);
    setEditingAddressId(null);
    setErrorMessage(null);
    setRecipientName("");
    setRecipientPhone("");
    setProvinceCode("79");
    setDistrictCode("760");
    setWardCode("26734");
    setFullAddress("");
    setLatitudeInput("10.775658");
    setLongitudeInput("106.700424");
    setIsDefault(false);
  }

  function handleOpenCreate() {
    resetForm();
    setIsDefault(addresses.length === 0);
    setIsFormOpen(true);
  }

  function handleOpenEdit(addr: UserAddressDto) {
    setErrorMessage(null);
    setEditingAddressId(addr.id);
    setRecipientName(addr.recipientName);
    setRecipientPhone(addr.recipientPhone);
    setProvinceCode(addr.provinceCode);
    setDistrictCode(addr.districtCode);
    setWardCode(addr.wardCode);
    setFullAddress(addr.fullAddress);
    setLatitudeInput(String(addr.latitude));
    setLongitudeInput(String(addr.longitude));
    setIsDefault(addr.isDefault);
    setIsFormOpen(true);
  }

  function handleProvinceChange(nextProvinceCode: string) {
    setProvinceCode(nextProvinceCode);
    const firstDistrict = DISTRICT_PRESETS.find(
      (d) => d.provinceCode === nextProvinceCode
    );
    if (firstDistrict) {
      setDistrictCode(firstDistrict.code);
      setWardCode(firstDistrict.defaultWardCode);
      setLatitudeInput(String(firstDistrict.latitude));
      setLongitudeInput(String(firstDistrict.longitude));
      return;
    }
    const province = PROVINCE_PRESETS.find((p) => p.code === nextProvinceCode);
    if (province) {
      setLatitudeInput(String(province.latitude));
      setLongitudeInput(String(province.longitude));
    }
  }

  function handleDistrictChange(nextDistrictCode: string) {
    setDistrictCode(nextDistrictCode);
    const district = DISTRICT_PRESETS.find((d) => d.code === nextDistrictCode);
    if (district) {
      setWardCode(district.defaultWardCode);
      setLatitudeInput(String(district.latitude));
      setLongitudeInput(String(district.longitude));
    }
  }

  function handleUseBrowserGps() {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      setErrorMessage("Trình duyệt không hỗ trợ định vị GPS.");
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLatitudeInput(pos.coords.latitude.toFixed(6));
        setLongitudeInput(pos.coords.longitude.toFixed(6));
      },
      () => {
        setErrorMessage(
          "Không thể lấy tọa độ GPS từ trình duyệt. Vui lòng chọn trên danh sách hoặc nhập tay."
        );
      }
    );
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setErrorMessage(null);

    const parsedLat = Number(latitudeInput);
    const parsedLon = Number(longitudeInput);

    const validation = upsertAddressSchema.safeParse({
      recipientName,
      recipientPhone,
      provinceCode,
      districtCode,
      wardCode,
      fullAddress,
      latitude: parsedLat,
      longitude: parsedLon,
      isDefault,
    });

    if (!validation.success) {
      setErrorMessage(validation.error.issues[0]?.message || "Dữ liệu địa chỉ không hợp lệ.");
      return;
    }

    const payload: UpsertUserAddressRequest = validation.data;

    if (editingAddressId) {
      updateMutation.mutate(
        { id: editingAddressId, payload },
        { onError: (err) => setErrorMessage(parseApiError(err)) }
      );
    } else {
      createMutation.mutate(payload, {
        onError: (err) => setErrorMessage(parseApiError(err)),
      });
    }
  }

  function handleSetDefault(addr: UserAddressDto) {
    if (addr.isDefault) return;
    updateMutation.mutate(
      {
        id: addr.id,
        payload: {
          recipientName: addr.recipientName,
          recipientPhone: addr.recipientPhone,
          fullAddress: addr.fullAddress,
          wardCode: addr.wardCode,
          districtCode: addr.districtCode,
          provinceCode: addr.provinceCode,
          latitude: addr.latitude,
          longitude: addr.longitude,
          isDefault: true,
        },
      },
      { onError: (err) => setErrorMessage(parseApiError(err)) }
    );
  }

  function handleDelete(addr: UserAddressDto) {
    deleteMutation.mutate(addr.id, {
      onError: (err) => setErrorMessage(parseApiError(err)),
    });
  }

  return (
    <section className="bg-white rounded-2xl border border-[#E6E4E0] p-6 lg:p-7 shadow-xs space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3 pb-4 border-b border-stone-100">
        <div>
          <div className="flex items-center gap-2">
            <MapPin className="w-5 h-5 text-[#D97706]" />
            <h2 className="text-lg font-extrabold text-[#111111] tracking-tight">
              Sổ địa chỉ giao nhận
            </h2>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-stone-100 text-stone-700">
              {addresses.length} địa chỉ
            </span>
          </div>
          <p className="text-xs text-stone-500 mt-0.5">
            Quản lý địa chỉ giao cuộn phim tới Lab và nhận hàng máy ảnh ký quỹ Escrow (tối đa{" "}
            {MAX_ADDRESSES} địa chỉ)
          </p>
        </div>

        {!isFormOpen && (
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={addresses.length >= MAX_ADDRESSES}
            leftIcon={<Plus className="w-4 h-4 text-[#D97706]" />}
            onClick={handleOpenCreate}
            className="border-amber-200 bg-[#FFF7ED] text-[#B45309] hover:bg-amber-100/80"
          >
            Thêm địa chỉ mới
          </Button>
        )}
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

      {/* Danh sách thẻ địa chỉ đã lưu */}
      {isLoading ? (
        <p className="text-xs text-stone-500 py-4">Đang tải sổ địa chỉ...</p>
      ) : addresses.length === 0 ? (
        <div className="p-6 text-center rounded-xl bg-[#FAF8F5] border border-dashed border-stone-300 text-xs text-stone-500">
          Bạn chưa lưu địa chỉ giao nhận nào. Hãy thêm địa chỉ đầu tiên để đặt shipper lấy film tận
          nơi.
        </div>
      ) : (
        <div className="space-y-3.5">
          {addresses.map((addr) => (
            <div
              key={addr.id}
              className={cn(
                "p-4 rounded-xl transition-all",
                addr.isDefault
                  ? "bg-[#FFFDF9] border-2 border-amber-300 shadow-2xs"
                  : "bg-white border border-stone-200 hover:border-stone-300"
              )}
            >
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-2">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-extrabold text-[#111111]">
                    {addr.recipientName}
                  </span>
                  <span className="text-xs font-semibold text-stone-500 font-mono inline-flex items-center gap-1">
                    <Phone className="w-3 h-3" />
                    {addr.recipientPhone}
                  </span>
                  {addr.isDefault && (
                    <span className="px-2 py-0.5 rounded text-[10px] font-extrabold uppercase bg-amber-100 text-amber-900 border border-amber-300">
                      Mặc định
                    </span>
                  )}
                </div>

                <div className="flex items-center gap-2">
                  <span className="inline-flex items-center gap-1 text-[11px] font-mono font-semibold bg-[#ECFDF5] text-[#047857] px-2 py-0.5 rounded border border-[#A7F3D0]">
                    <Compass className="w-3 h-3 text-[#059669]" />
                    {addr.latitude.toFixed(4)}° N, {addr.longitude.toFixed(4)}° E
                  </span>
                </div>
              </div>

              <p className="text-xs font-medium text-stone-700 leading-relaxed">
                {addr.fullAddress}
              </p>

              <div className="mt-2.5 pt-2.5 border-t border-stone-100 flex flex-wrap items-center justify-between gap-2 text-[11px]">
                <span className="text-stone-400 font-mono">
                  Mã hành chính: Tỉnh {addr.provinceCode} • Quận {addr.districtCode} • Phường{" "}
                  {addr.wardCode}
                </span>

                <div className="flex items-center gap-2">
                  {!addr.isDefault && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      leftIcon={<Star className="w-3.5 h-3.5 text-[#D97706]" />}
                      onClick={() => handleSetDefault(addr)}
                      className="h-7 px-2.5 text-xs"
                    >
                      Đặt mặc định
                    </Button>
                  )}
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    aria-label={`Sửa địa chỉ ${addr.recipientName}`}
                    leftIcon={<Edit3 className="w-3.5 h-3.5" />}
                    onClick={() => handleOpenEdit(addr)}
                    className="h-7 px-2.5 text-xs"
                  >
                    Sửa
                  </Button>
                  <Button
                    type="button"
                    variant="safelight"
                    size="sm"
                    aria-label={`Xóa địa chỉ ${addr.recipientName}`}
                    leftIcon={<Trash2 className="w-3.5 h-3.5" />}
                    onClick={() => handleDelete(addr)}
                    className="h-7 px-2.5 text-xs"
                  >
                    Xóa
                  </Button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* FORM THÊM / CẬP NHẬT ĐỊA CHỈ (UpsertUserAddressRequest) */}
      {isFormOpen && (
        <form
          onSubmit={handleSubmit}
          noValidate
          className="bg-stone-50/80 rounded-xl border border-stone-200 p-5 space-y-4"
        >
          <div className="flex items-center justify-between pb-2.5 border-b border-stone-200">
            <h3 className="text-xs font-extrabold uppercase tracking-wider text-[#111111] flex items-center gap-1.5">
              <MapPin className="w-4 h-4 text-[#D97706]" />
              <span>
                {editingAddressId
                  ? "Cập nhật địa chỉ giao nhận (UpsertUserAddressRequest)"
                  : "Thêm địa chỉ giao nhận mới (UpsertUserAddressRequest)"}
              </span>
            </h3>
            <Badge variant="amber" size="sm">
              GPS Haversine Ready
            </Badge>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              label="Tên người nhận"
              required
              placeholder="Nguyễn Gia Huy"
              value={recipientName}
              onChange={(e) => setRecipientName(e.target.value)}
            />
            <Input
              label="Số điện thoại người nhận"
              required
              placeholder="0912345678"
              value={recipientPhone}
              onChange={(e) => setRecipientPhone(e.target.value)}
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="space-y-1.5">
              <label
                htmlFor="address-province-select"
                className="block text-xs font-bold text-[#111111] uppercase tracking-wider"
              >
                Tỉnh / Thành phố <span className="text-[#E11D48]">*</span>
              </label>
              <select
                id="address-province-select"
                value={provinceCode}
                onChange={(e) => handleProvinceChange(e.target.value)}
                className="w-full h-11 rounded-xl bg-white border border-stone-300 px-3 text-sm font-medium text-[#111111] focus:outline-none focus:border-[#D97706] focus:ring-4 focus:ring-amber-100/80"
              >
                {PROVINCE_PRESETS.map((prov) => (
                  <option key={prov.code} value={prov.code}>
                    {prov.name} ({prov.code})
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <label
                htmlFor="address-district-select"
                className="block text-xs font-bold text-[#111111] uppercase tracking-wider"
              >
                Quận / Huyện <span className="text-[#E11D48]">*</span>
              </label>
              <select
                id="address-district-select"
                value={districtCode}
                onChange={(e) => handleDistrictChange(e.target.value)}
                className="w-full h-11 rounded-xl bg-white border border-stone-300 px-3 text-sm font-medium text-[#111111] focus:outline-none focus:border-[#D97706] focus:ring-4 focus:ring-amber-100/80"
              >
                {availableDistricts.map((dist) => (
                  <option key={dist.code} value={dist.code}>
                    {dist.name} ({dist.code})
                  </option>
                ))}
              </select>
            </div>

            <Input
              label="Mã Phường / Xã"
              required
              placeholder="26734"
              value={wardCode}
              onChange={(e) => setWardCode(e.target.value)}
            />
          </div>

          <Input
            label="Địa chỉ chi tiết"
            required
            placeholder="Số 42 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh"
            value={fullAddress}
            onChange={(e) => setFullAddress(e.target.value)}
          />

          {/* Bộ chọn Kinh độ / Vĩ độ GPS */}
          <div className="p-3.5 rounded-xl bg-white border border-stone-200 space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="text-xs font-bold text-stone-700 flex items-center gap-1.5">
                <Compass className="w-4 h-4 text-[#059669]" />
                <span>Tọa độ vị trí giao hàng GPS (Shipper nội thành)</span>
              </span>
              <button
                type="button"
                onClick={handleUseBrowserGps}
                className="inline-flex items-center gap-1.5 px-2.5 py-1 text-[11px] font-bold text-[#065F46] bg-[#ECFDF5] hover:bg-emerald-100 border border-[#A7F3D0] rounded-lg transition-colors cursor-pointer"
              >
                <Navigation className="w-3 h-3 text-[#059669]" />
                <span>Lấy vị trí GPS hiện tại</span>
              </button>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Input
                label="Vĩ độ (Latitude)"
                type="number"
                step="0.000001"
                value={latitudeInput}
                onChange={(e) => setLatitudeInput(e.target.value)}
              />
              <Input
                label="Kinh độ (Longitude)"
                type="number"
                step="0.000001"
                value={longitudeInput}
                onChange={(e) => setLongitudeInput(e.target.value)}
              />
            </div>
          </div>

          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-2">
            <label className="flex items-center gap-2.5 text-xs font-bold text-stone-800 cursor-pointer select-none">
              <input
                type="checkbox"
                checked={isDefault}
                onChange={(e) => setIsDefault(e.target.checked)}
                className="w-4 h-4 rounded border-stone-300 accent-[#D97706]"
              />
              <span>Đặt làm địa chỉ mặc định</span>
            </label>

            <div className="flex items-center gap-2.5">
              <Button type="button" variant="secondary" size="sm" onClick={resetForm}>
                Hủy
              </Button>
              <Button
                type="submit"
                variant="primary"
                size="sm"
                isLoading={createMutation.isPending || updateMutation.isPending}
              >
                Lưu địa chỉ
              </Button>
            </div>
          </div>
        </form>
      )}
    </section>
  );
};
