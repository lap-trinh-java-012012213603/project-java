import { z } from "zod";

export const VN_PHONE_REGEX = /^(0|\+84)(3|5|7|8|9)[0-9]{8}$/;

export const STRONG_PASSWORD_REGEX =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&_#-])[A-Za-z\d@$!%*?&_#-]{8,72}$/;

export interface PasswordCriteriaStatus {
  lengthValid: boolean;
  caseValid: boolean;
  digitValid: boolean;
  specialValid: boolean;
}

export function evaluatePasswordStrength(password: string): PasswordCriteriaStatus {
  return {
    lengthValid: password.length >= 8 && password.length <= 72,
    caseValid: /[a-z]/.test(password) && /[A-Z]/.test(password),
    digitValid: /\d/.test(password),
    specialValid: /[@$!%*?&_#-]/.test(password),
  };
}

export const loginSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, "Email không được để trống")
    .email("Định dạng email không hợp lệ")
    .max(150, "Email không được vượt quá 150 ký tự"),
  password: z
    .string()
    .min(1, "Mật khẩu không được để trống")
    .max(72, "Mật khẩu không được vượt quá 72 ký tự"),
});

export type LoginFormValues = z.infer<typeof loginSchema>;

export const registerSchema = z
  .object({
    fullName: z
      .string()
      .trim()
      .min(1, "Họ và tên không được để trống")
      .min(2, "Họ và tên phải từ 2 đến 120 ký tự")
      .max(120, "Họ và tên phải từ 2 đến 120 ký tự"),
    email: z
      .string()
      .trim()
      .min(1, "Email không được để trống")
      .email("Định dạng email không hợp lệ")
      .max(150, "Email không được vượt quá 150 ký tự"),
    phoneNumber: z
      .string()
      .trim()
      .min(1, "Số điện thoại không được để trống")
      .regex(
        VN_PHONE_REGEX,
        "Số điện thoại di động Việt Nam không hợp lệ (ví dụ: 0908123456)"
      ),
    password: z
      .string()
      .min(1, "Mật khẩu không được để trống")
      .min(8, "Mật khẩu phải từ 8 đến 72 ký tự")
      .max(72, "Mật khẩu phải từ 8 đến 72 ký tự")
      .regex(
        STRONG_PASSWORD_REGEX,
        "Mật khẩu phải bao gồm ít nhất 1 chữ hoa, 1 chữ thường, 1 chữ số và 1 ký tự đặc biệt (@$!%*?&_#-)"
      ),
    confirmPassword: z.string().min(1, "Vui lòng xác nhận lại mật khẩu"),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ["confirmPassword"],
    message: "Mật khẩu xác nhận không khớp",
  });

export type RegisterFormValues = z.infer<typeof registerSchema>;

export const changePasswordSchema = z
  .object({
    currentPassword: z
      .string()
      .min(1, "Vui lòng nhập mật khẩu hiện tại")
      .max(72, "Mật khẩu hiện tại không vượt quá 72 ký tự"),
    newPassword: z
      .string()
      .min(1, "Vui lòng nhập mật khẩu mới")
      .min(8, "Mật khẩu mới phải từ 8 đến 72 ký tự")
      .max(72, "Mật khẩu mới phải từ 8 đến 72 ký tự")
      .regex(
        STRONG_PASSWORD_REGEX,
        "Mật khẩu mới phải bao gồm ít nhất 1 chữ hoa, 1 chữ thường, 1 chữ số và 1 ký tự đặc biệt (@$!%*?&_#-)"
      ),
    confirmNewPassword: z.string().min(1, "Vui lòng xác nhận mật khẩu mới"),
  })
  .refine((values) => values.newPassword === values.confirmNewPassword, {
    path: ["confirmNewPassword"],
    message: "Mật khẩu xác nhận không khớp",
  });

export type ChangePasswordFormValues = z.infer<typeof changePasswordSchema>;
