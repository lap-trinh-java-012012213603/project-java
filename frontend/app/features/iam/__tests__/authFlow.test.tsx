import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import AuthLayout from "@/layouts/AuthLayout";
import LoginPage from "@/routes/auth/login";
import OAuth2CallbackPage from "@/routes/auth/oauth2-callback";
import RegisterPage from "@/routes/auth/register";
import { resolvePostAuthRedirect } from "@/features/iam/utils/authRedirect";
import { httpClient } from "@/shared/api/httpClient";
import { useAuthStore } from "@/shared/stores/useAuthStore";

function renderAuthRouter(initialEntry: string) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  const router = createMemoryRouter(
    [
      {
        path: "/",
        element: <div data-testid="home-destination">Trang Chủ AnalogHub</div>,
      },
      {
        path: "/customer/bookings/new",
        element: <div data-testid="booking-destination">Trang Đặt Tráng Phim</div>,
      },
      {
        path: "/customer/labs",
        element: <div data-testid="labs-destination">Danh Sách Phòng Lab</div>,
      },
      {
        element: <AuthLayout />,
        children: [
          { path: "/auth/login", element: <LoginPage /> },
          { path: "/auth/register", element: <RegisterPage /> },
          { path: "/auth/oauth2/callback", element: <OAuth2CallbackPage /> },
        ],
      },
    ],
    { initialEntries: [initialEntry] }
  );

  return {
    router,
    ...render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    ),
  };
}

describe("FE-C05 AuthLayout, LoginForm, RegisterForm & Google OAuth2 Callback", () => {
  beforeEach(() => {
    localStorage.clear();
    useAuthStore.getState().logout();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("validates Vietnamese error messages in LoginForm before submitting", async () => {
    const postSpy = vi.spyOn(httpClient, "post");
    renderAuthRouter("/auth/login");

    fireEvent.click(screen.getByRole("button", { name: /^Đăng nhập$/i }));

    await waitFor(() => {
      expect(screen.getByText(/Email không được để trống/i)).toBeTruthy();
      expect(screen.getByText(/Mật khẩu không được để trống/i)).toBeTruthy();
    });

    fireEvent.change(screen.getByLabelText(/Email/i), {
      target: { value: "dia-chi-email-sai" },
    });
    fireEvent.change(screen.getByLabelText(/^Mật khẩu/i), {
      target: { value: "Password@123" },
    });
    fireEvent.click(screen.getByRole("button", { name: /^Đăng nhập$/i }));

    await waitFor(() => {
      expect(screen.getByText(/Định dạng email không hợp lệ/i)).toBeTruthy();
    });
    expect(postSpy).not.toHaveBeenCalled();
  });

  it("logs in successfully, persists tokens + labIds into useAuthStore, and redirects back to previous page", async () => {
    const postSpy = vi.spyOn(httpClient, "post").mockResolvedValueOnce({
      data: {
        success: true,
        code: "SUCCESS",
        message: "Đăng nhập thành công.",
        data: {
          accessToken: "jwt-login-access-001",
          refreshToken: "rft-login-refresh-001",
          tokenType: "Bearer",
          expiresInSeconds: 900,
          refreshExpiresInSeconds: 2592000,
          user: {
            id: "0192e8f0-3b4a-7c11-8a22-3f4e5a6b7c8e",
            email: "minh.nguyen@analoghub.vn",
            phoneNumber: "0908123456",
            fullName: "Nguyễn Quang Minh",
            avatarUrl: null,
            accountStatus: "ACTIVE",
            authProvider: "LOCAL",
            roles: ["CUSTOMER", "LAB_OWNER"],
            labMemberships: [
              { labId: "lab-hanoi-01", memberRole: "OWNER" },
            ],
            createdAt: "2026-11-08T10:00:00Z",
          },
        },
      },
    });

    const { router } = renderAuthRouter("/auth/login?redirect=%2Fcustomer%2Fbookings%2Fnew");

    fireEvent.change(screen.getByLabelText(/Email/i), {
      target: { value: "minh.nguyen@analoghub.vn" },
    });
    fireEvent.change(screen.getByLabelText(/^Mật khẩu/i), {
      target: { value: "Analog@2026!Secure" },
    });
    fireEvent.click(screen.getByRole("button", { name: /^Đăng nhập$/i }));

    await waitFor(() => {
      expect(screen.getByTestId("booking-destination")).toBeTruthy();
    });

    expect(postSpy).toHaveBeenCalledWith(
      "/api/v1/auth/login",
      expect.objectContaining({
        email: "minh.nguyen@analoghub.vn",
        password: "Analog@2026!Secure",
      })
    );

    const authState = useAuthStore.getState();
    expect(authState.isAuthenticated).toBe(true);
    expect(authState.accessToken).toBe("jwt-login-access-001");
    expect(authState.refreshToken).toBe("rft-login-refresh-001");
    expect(authState.user?.fullName).toBe("Nguyễn Quang Minh");
    expect(authState.user?.labIds).toEqual(["lab-hanoi-01"]);
    expect(authState.user?.activeLabId).toBe("lab-hanoi-01");
    expect(router.state.location.pathname).toBe("/customer/bookings/new");
  });

  it("validates Vietnamese phone/password rules in RegisterForm and registers a new account", async () => {
    const postSpy = vi.spyOn(httpClient, "post").mockResolvedValueOnce({
      data: {
        success: true,
        code: "SUCCESS",
        message: "Đăng ký tài khoản thành công.",
        data: {
          accessToken: "jwt-register-access-002",
          refreshToken: "rft-register-refresh-002",
          tokenType: "Bearer",
          expiresInSeconds: 900,
          refreshExpiresInSeconds: 2592000,
          user: {
            id: "0192e8f0-9999-7c11-8a22-3f4e5a6b7c8e",
            email: "lan.pham@analoghub.vn",
            phoneNumber: "0912345678",
            fullName: "Phạm Hương Lan",
            avatarUrl: null,
            accountStatus: "ACTIVE",
            authProvider: "LOCAL",
            roles: ["CUSTOMER"],
            labMemberships: [],
            createdAt: "2026-11-08T10:05:00Z",
          },
        },
      },
    });

    renderAuthRouter("/auth/register?redirect=%2Fcustomer%2Flabs");

    // Thử nhập SĐT sai và mật khẩu xác nhận không khớp trước
    fireEvent.change(screen.getByLabelText(/Họ và tên/i), {
      target: { value: "Phạm Hương Lan" },
    });
    fireEvent.change(screen.getByLabelText(/Email/i), {
      target: { value: "lan.pham@analoghub.vn" },
    });
    fireEvent.change(screen.getByLabelText(/Số điện thoại/i), {
      target: { value: "12345" },
    });
    fireEvent.change(screen.getByLabelText(/^Mật khẩu/i), {
      target: { value: "Analog@2026" },
    });
    fireEvent.change(screen.getByLabelText(/Xác nhận mật khẩu/i), {
      target: { value: "KhacMatKhau@2026" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Tạo tài khoản/i }));

    await waitFor(() => {
      expect(screen.getByText(/Số điện thoại di động Việt Nam không hợp lệ/i)).toBeTruthy();
      expect(screen.getByText(/Mật khẩu xác nhận không khớp/i)).toBeTruthy();
    });
    expect(postSpy).not.toHaveBeenCalled();

    // Sửa lại SĐT và mật khẩu xác nhận hợp lệ
    fireEvent.change(screen.getByLabelText(/Số điện thoại/i), {
      target: { value: "0912345678" },
    });
    fireEvent.change(screen.getByLabelText(/Xác nhận mật khẩu/i), {
      target: { value: "Analog@2026" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Tạo tài khoản/i }));

    await waitFor(() => {
      expect(screen.getByTestId("labs-destination")).toBeTruthy();
    });

    expect(postSpy).toHaveBeenCalledWith(
      "/api/v1/auth/register",
      expect.objectContaining({
        fullName: "Phạm Hương Lan",
        email: "lan.pham@analoghub.vn",
        phoneNumber: "0912345678",
        password: "Analog@2026",
      })
    );
    expect(useAuthStore.getState().accessToken).toBe("jwt-register-access-002");
  });

  it("handles Google OAuth2 callback, exchanges id_token with backend, saves session and redirects", async () => {
    const postSpy = vi.spyOn(httpClient, "post").mockResolvedValueOnce({
      data: {
        success: true,
        code: "SUCCESS",
        message: "Đăng nhập qua Google thành công.",
        data: {
          accessToken: "jwt-google-access-003",
          refreshToken: "rft-google-refresh-003",
          tokenType: "Bearer",
          expiresInSeconds: 900,
          refreshExpiresInSeconds: 2592000,
          isNewUser: false,
          user: {
            id: "0192e8f0-7777-7c11-8a22-3f4e5a6b7c8e",
            email: "google.photographer@analoghub.vn",
            phoneNumber: null,
            fullName: "Trần Quang Hải",
            avatarUrl: "https://lh3.googleusercontent.com/a/avatar-hai",
            accountStatus: "ACTIVE",
            authProvider: "GOOGLE",
            roles: ["CUSTOMER"],
            labMemberships: [],
            createdAt: "2026-11-08T10:10:00Z",
          },
        },
      },
    });

    renderAuthRouter(
      "/auth/oauth2/callback#id_token=google-oidc-id-token-abc123&state=%2Fcustomer%2Flabs"
    );

    await waitFor(() => {
      expect(screen.getByTestId("labs-destination")).toBeTruthy();
    });

    expect(postSpy).toHaveBeenCalledWith(
      "/api/v1/auth/oauth2/google",
      expect.objectContaining({
        idToken: "google-oidc-id-token-abc123",
      })
    );

    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(true);
    expect(state.accessToken).toBe("jwt-google-access-003");
    expect(state.refreshToken).toBe("rft-google-refresh-003");
    expect(state.user?.avatarUrl).toBe("https://lh3.googleusercontent.com/a/avatar-hai");
  });

  it("prevents open-redirect attacks when resolving post-login redirect URLs", () => {
    expect(
      resolvePostAuthRedirect(new URLSearchParams("redirect=https://evil.example.com"), null)
    ).toBe("/");
    expect(resolvePostAuthRedirect(new URLSearchParams("redirect=//evil.example.com"), null)).toBe(
      "/"
    );
    expect(resolvePostAuthRedirect(new URLSearchParams("redirect=/auth/login"), null)).toBe("/");
    expect(
      resolvePostAuthRedirect(
        new URLSearchParams("redirect=%2Fcustomer%2Farchive%3Froll%3D36"),
        null
      )
    ).toBe("/customer/archive?roll=36");
  });
});
