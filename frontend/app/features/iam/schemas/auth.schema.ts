import { z } from "zod";

export const VN_PHONE_REGEX = /^(0|\+84)(3|5|7|8|9)[0-9]{8}$/;

export const STRONG_PASSWORD_REGEX =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&_#-])[A-Za-z\d@$!%*?&_#-]{8,72}$/;

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
