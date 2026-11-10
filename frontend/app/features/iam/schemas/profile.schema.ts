import { z } from "zod";
import { VN_PHONE_REGEX } from "./auth.schema";

export const AVATAR_KEY_REGEX = /^public\/avatars\/.*\.(jpg|jpeg|png|webp)$/i;

export const FILM_FORMAT_OPTIONS = [
  { code: "FMT_135", label: "35mm (135)" },
  { code: "FMT_120", label: "Medium Format 120" },
  { code: "FMT_SHEET", label: "Large Format (4x5 / 8x10)" },
] as const;

export const CHEMICAL_PROCESS_OPTIONS = [
  { code: "C41", label: "C-41 (Màu âm bản)" },
  { code: "ECN2", label: "ECN-2 (Phim điện ảnh)" },
  { code: "BW", label: "B&W (Đen trắng thủ công)" },
  { code: "E6", label: "E-6 (Phim dương bản / Slide)" },
] as const;

export const COLOR_TONE_OPTIONS = [
  { code: "WARM_PASTEL", label: "Warm Pastel (Kodak Portra)" },
  { code: "CINEMATIC_TEAL_ORANGE", label: "Cinematic Teal & Orange (Vision3)" },
  { code: "HIGH_CONTRAST_BW", label: "High Contrast B&W (Tri-X / HP5)" },
  { code: "FINE_GRAIN_PORTRAIT", label: "Fine Grain Portrait (Ektar / Pro 400H)" },
  { code: "VINTAGE_FADED", label: "Vintage Faded (ColorPlus / Gold 200)" },
] as const;

export const SHOOTING_LEVEL_OPTIONS = [
  { value: "BEGINNER", label: "Mới bắt đầu (Beginner)" },
  { value: "INTERMEDIATE", label: "Bán chuyên (Intermediate)" },
  { value: "ADVANCED", label: "Chuyên sâu buồng tối (Advanced)" },
  { value: "PROFESSIONAL", label: "Nhiếp ảnh gia chuyên nghiệp (Professional)" },
] as const;

export const EXPERT_SPECIALTY_OPTIONS = [
  { code: "DARKROOM_PRINTING", label: "Rọi ảnh buồng tối (Darkroom Printing)" },
  { code: "C41_CHEMISTRY", label: "Hóa chất tráng màu C-41 / E-6" },
  { code: "ECN2_CINEMA", label: "Xử lý lớp than Remjet & ECN-2" },
  { code: "BW_ZONE_SYSTEM", label: "Hệ thống sắc độ B&W Zone System" },
  { code: "CAMERA_REPAIR", label: "Cơ khí & Chuẩn đoán thân máy Film" },
  { code: "VINTAGE_LENS_OPTICS", label: "Quang học ống kính cổ điển (Vintage Lens)" },
] as const;

export interface GeoPreset {
  code: string;
  name: string;
  latitude: number;
  longitude: number;
}

export interface DistrictPreset extends GeoPreset {
  provinceCode: string;
  defaultWardCode: string;
}

export const PROVINCE_PRESETS: GeoPreset[] = [
  { code: "79", name: "TP. Hồ Chí Minh", latitude: 10.776889, longitude: 106.700806 },
  { code: "01", name: "Hà Nội", latitude: 21.028511, longitude: 105.804817 },
  { code: "48", name: "Đà Nẵng", latitude: 16.054407, longitude: 108.202167 },
  { code: "68", name: "Lâm Đồng (Đà Lạt)", latitude: 11.940419, longitude: 108.458313 },
  { code: "46", name: "Thừa Thiên Huế", latitude: 16.463713, longitude: 107.590866 },
  { code: "56", name: "Khánh Hòa (Nha Trang)", latitude: 12.238791, longitude: 109.196749 },
  { code: "31", name: "Hải Phòng", latitude: 20.844912, longitude: 106.688084 },
  { code: "92", name: "Cần Thơ", latitude: 10.045162, longitude: 105.746854 },
  { code: "74", name: "Bình Dương", latitude: 10.980461, longitude: 106.651856 },
  { code: "75", name: "Đồng Nai", latitude: 10.957413, longitude: 106.84269 },
  { code: "77", name: "Bà Rịa - Vũng Tàu", latitude: 10.34599, longitude: 107.08426 },
];

export const DISTRICT_PRESETS: DistrictPreset[] = [
  // TP. Hồ Chí Minh (79)
  { code: "760", provinceCode: "79", name: "Quận 1", defaultWardCode: "26734", latitude: 10.775658, longitude: 106.700424 },
  { code: "770", provinceCode: "79", name: "Quận 3", defaultWardCode: "27127", latitude: 10.78437, longitude: 106.684409 },
  { code: "773", provinceCode: "79", name: "Quận 4", defaultWardCode: "27259", latitude: 10.757826, longitude: 106.701297 },
  { code: "774", provinceCode: "79", name: "Quận 5", defaultWardCode: "27301", latitude: 10.754028, longitude: 106.663375 },
  { code: "771", provinceCode: "79", name: "Quận 10", defaultWardCode: "27160", latitude: 10.772109, longitude: 106.667954 },
  { code: "765", provinceCode: "79", name: "Quận Bình Thạnh", defaultWardCode: "26905", latitude: 10.810583, longitude: 106.709142 },
  { code: "768", provinceCode: "79", name: "Quận Phú Nhuận", defaultWardCode: "27043", latitude: 10.799194, longitude: 106.680288 },
  { code: "766", provinceCode: "79", name: "Quận Tân Bình", defaultWardCode: "26965", latitude: 10.801466, longitude: 106.652597 },
  { code: "778", provinceCode: "79", name: "Quận 7", defaultWardCode: "27466", latitude: 10.734034, longitude: 106.721579 },
  { code: "769", provinceCode: "79", name: "TP. Thủ Đức", defaultWardCode: "26830", latitude: 10.823099, longitude: 106.758435 },
  // Hà Nội (01)
  { code: "001", provinceCode: "01", name: "Quận Ba Đình", defaultWardCode: "00001", latitude: 21.034085, longitude: 105.819454 },
  { code: "002", provinceCode: "01", name: "Quận Hoàn Kiếm", defaultWardCode: "00037", latitude: 21.028511, longitude: 105.854167 },
  { code: "003", provinceCode: "01", name: "Quận Tây Hồ", defaultWardCode: "00091", latitude: 21.066695, longitude: 105.814141 },
  { code: "005", provinceCode: "01", name: "Quận Cầu Giấy", defaultWardCode: "00157", latitude: 21.030653, longitude: 105.788551 },
  { code: "006", provinceCode: "01", name: "Quận Đống Đa", defaultWardCode: "00181", latitude: 21.018072, longitude: 105.829949 },
  { code: "007", provinceCode: "01", name: "Quận Hai Bà Trưng", defaultWardCode: "00241", latitude: 21.006382, longitude: 105.855132 },
  // Đà Nẵng (48) & Đà Lạt (68) & Khác
  { code: "490", provinceCode: "48", name: "Quận Hải Châu", defaultWardCode: "20194", latitude: 16.054407, longitude: 108.202167 },
  { code: "492", provinceCode: "48", name: "Quận Sơn Trà", defaultWardCode: "20233", latitude: 16.06778, longitude: 108.2356 },
  { code: "672", provinceCode: "68", name: "TP. Đà Lạt", defaultWardCode: "24769", latitude: 11.940419, longitude: 108.458313 },
  { code: "474", provinceCode: "46", name: "TP. Huế", defaultWardCode: "19744", latitude: 16.463713, longitude: 107.590866 },
  { code: "568", provinceCode: "56", name: "TP. Nha Trang", defaultWardCode: "22339", latitude: 12.238791, longitude: 109.196749 },
  { code: "303", provinceCode: "31", name: "Quận Hồng Bàng", defaultWardCode: "11329", latitude: 20.844912, longitude: 106.688084 },
  { code: "916", provinceCode: "92", name: "Quận Ninh Kiều", defaultWardCode: "31117", latitude: 10.045162, longitude: 105.746854 },
  { code: "718", provinceCode: "74", name: "TP. Thủ Dầu Một", defaultWardCode: "25741", latitude: 10.980461, longitude: 106.651856 },
  { code: "731", provinceCode: "75", name: "TP. Biên Hòa", defaultWardCode: "25993", latitude: 10.957413, longitude: 106.84269 },
  { code: "747", provinceCode: "77", name: "TP. Vũng Tàu", defaultWardCode: "26506", latitude: 10.34599, longitude: 107.08426 },
];

export const updateProfileSchema = z.object({
  fullName: z
    .string()
    .trim()
    .min(1, "Họ và tên không được để trống")
    .min(2, "Họ và tên phải từ 2 đến 120 ký tự")
    .max(120, "Họ và tên phải từ 2 đến 120 ký tự"),
  phoneNumber: z
    .string()
    .trim()
    .min(1, "Số điện thoại không được để trống")
    .regex(
      VN_PHONE_REGEX,
      "Số điện thoại di động Việt Nam không hợp lệ (ví dụ: 0908123456)"
    ),
  avatarKey: z
    .string()
    .trim()
    .max(255, "Khóa ảnh đại diện không được vượt quá 255 ký tự")
    .refine(
      (val) => val.length === 0 || AVATAR_KEY_REGEX.test(val),
      "avatarKey phải bắt đầu bằng 'public/avatars/' và có định dạng jpg, jpeg, png hoặc webp"
    )
    .optional(),
  shootingLevel: z.string().max(64).optional(),
  favoriteCamerasInput: z.string().optional(),
});

export type UpdateProfileFormValues = z.infer<typeof updateProfileSchema>;

export const upsertAddressSchema = z.object({
  recipientName: z
    .string()
    .trim()
    .min(1, "Tên người nhận không được để trống")
    .min(2, "Tên người nhận phải từ 2 đến 120 ký tự")
    .max(120, "Tên người nhận phải từ 2 đến 120 ký tự"),
  recipientPhone: z
    .string()
    .trim()
    .min(1, "Số điện thoại người nhận không được để trống")
    .regex(VN_PHONE_REGEX, "Số điện thoại người nhận không hợp lệ"),
  provinceCode: z
    .string()
    .trim()
    .min(1, "Mã tỉnh/thành phố không được để trống")
    .max(20, "Mã tỉnh/thành phố tối đa 20 ký tự"),
  districtCode: z
    .string()
    .trim()
    .min(1, "Mã quận/huyện không được để trống")
    .max(20, "Mã quận/huyện tối đa 20 ký tự"),
  wardCode: z
    .string()
    .trim()
    .min(1, "Mã phường/xã không được để trống")
    .max(20, "Mã phường/xã tối đa 20 ký tự"),
  fullAddress: z
    .string()
    .trim()
    .min(1, "Địa chỉ chi tiết không được để trống")
    .min(10, "Địa chỉ chi tiết phải từ 10 đến 300 ký tự")
    .max(300, "Địa chỉ chi tiết phải từ 10 đến 300 ký tự"),
  latitude: z
    .number({ invalid_type_error: "Vĩ độ phải là số thực hợp lệ" })
    .min(-90, "Vĩ độ (latitude) phải từ -90.0 đến 90.0")
    .max(90, "Vĩ độ (latitude) phải từ -90.0 đến 90.0"),
  longitude: z
    .number({ invalid_type_error: "Kinh độ phải là số thực hợp lệ" })
    .min(-180, "Kinh độ (longitude) phải từ -180.0 đến 180.0")
    .max(180, "Kinh độ (longitude) phải từ -180.0 đến 180.0"),
  isDefault: z.boolean(),
});

export type UpsertAddressFormValues = z.infer<typeof upsertAddressSchema>;
