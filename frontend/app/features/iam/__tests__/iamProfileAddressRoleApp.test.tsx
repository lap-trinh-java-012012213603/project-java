import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AddressBook } from "@/features/iam/components/AddressBook";
import { ChangePasswordCard } from "@/features/iam/components/ChangePasswordCard";
import { RoleApplicationModal } from "@/features/iam/components/RoleApplicationModal";
import { UserProfile } from "@/features/iam/components/UserProfile";
import { httpClient } from "@/shared/api/httpClient";
import { useAuthStore } from "@/shared/stores/useAuthStore";

function renderWithQueryClient(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe("FE-C06 UserProfile, AddressBook & RoleApplicationModal", () => {
  beforeEach(() => {
    localStorage.clear();
    useAuthStore.getState().setAuth(
      "jwt-test-token-001",
      {
        id: "0192e8f0-3b4a-7c11-8a22-3f4e5a6b7c8e",
        email: "minh.nguyen@analoghub.vn",
        fullName: "Nguyễn Quang Minh",
        roles: ["CUSTOMER"],
        labIds: [],
      },
      "rft-test-token-001"
    );
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("loads user profile, updates stylePreferences via PUT /api/v1/users/me, and syncs useAuthStore", async () => {
    const getSpy = vi.spyOn(httpClient, "get").mockImplementation(async (url: string) => {
      if (url.includes("/api/v1/users/me")) {
        return {
          data: {
            success: true,
            code: "SUCCESS",
            message: "Lấy thông tin hồ sơ cá nhân thành công.",
            data: {
              id: "0192e8f0-3b4a-7c11-8a22-3f4e5a6b7c8e",
              email: "minh.nguyen@analoghub.vn",
              phoneNumber: "0908123456",
              fullName: "Nguyễn Quang Minh",
              avatarKey: "public/avatars/2026/11/avatar_minh.webp",
              avatarUrl: "https://cdn.analoghub.vn/public/avatars/2026/11/avatar_minh.webp",
              accountStatus: "ACTIVE",
              authProvider: "LOCAL",
              roles: ["CUSTOMER"],
              labMemberships: [],
              stylePreferences: {
                favoriteFormats: ["FMT_135"],
                favoriteProcesses: ["C41"],
                preferredColorTones: ["WARM_PASTEL"],
                favoriteCameras: ["Leica M6"],
                shootingLevel: "INTERMEDIATE",
              },
              createdAt: "2026-11-08T10:00:00Z",
              updatedAt: "2026-11-08T10:00:00Z",
            },
          },
        };
      }
      return {
        data: {
          success: true,
          code: "SUCCESS",
          message: "OK",
          data: [],
        },
      };
    });

    const putSpy = vi.spyOn(httpClient, "put").mockResolvedValueOnce({
      data: {
        success: true,
        code: "SUCCESS",
        message: "Cập nhật hồ sơ cá nhân thành công.",
        data: {
          id: "0192e8f0-3b4a-7c11-8a22-3f4e5a6b7c8e",
          email: "minh.nguyen@analoghub.vn",
          phoneNumber: "0908999888",
          fullName: "Nguyễn Quang Minh (Analog Pro)",
          avatarKey: "public/avatars/2026/11/avatar_minh.webp",
          avatarUrl: "https://cdn.analoghub.vn/public/avatars/2026/11/avatar_minh.webp",
          accountStatus: "ACTIVE",
          authProvider: "LOCAL",
          roles: ["CUSTOMER"],
          labMemberships: [],
          stylePreferences: {
            favoriteFormats: ["FMT_135", "FMT_120"],
            favoriteProcesses: ["C41", "ECN2"],
            preferredColorTones: ["WARM_PASTEL"],
            favoriteCameras: ["Leica M6", "Hasselblad 500C/M"],
            shootingLevel: "ADVANCED",
          },
          createdAt: "2026-11-08T10:00:00Z",
          updatedAt: "2026-11-08T12:00:00Z",
        },
      },
    });

    renderWithQueryClient(<UserProfile />);

    await waitFor(() => {
      expect(screen.getByDisplayValue("Nguyễn Quang Minh")).toBeTruthy();
    });
    expect(getSpy).toHaveBeenCalledWith("/api/v1/users/me");

    fireEvent.change(screen.getByLabelText(/Họ và tên/i), {
      target: { value: "Nguyễn Quang Minh (Analog Pro)" },
    });
    fireEvent.change(screen.getByLabelText(/Số điện thoại/i), {
      target: { value: "0908999888" },
    });

    // Bật thêm khổ phim 120 và quy trình ECN-2
    fireEvent.click(screen.getByRole("button", { name: /Medium Format 120/i }));
    fireEvent.click(screen.getByRole("button", { name: /ECN-2/i }));

    // Đổi trình độ chụp sang ADVANCED
    fireEvent.change(screen.getByLabelText(/Trình độ chụp phim/i), {
      target: { value: "ADVANCED" },
    });

    // Cập nhật danh sách máy ảnh yêu thích
    fireEvent.change(screen.getByLabelText(/Máy ảnh yêu thích/i), {
      target: { value: "Leica M6, Hasselblad 500C/M" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Lưu hồ sơ/i }));

    await waitFor(() => {
      expect(putSpy).toHaveBeenCalledWith(
        "/api/v1/users/me",
        expect.objectContaining({
          fullName: "Nguyễn Quang Minh (Analog Pro)",
          phoneNumber: "0908999888",
          stylePreferences: expect.objectContaining({
            favoriteFormats: expect.arrayContaining(["FMT_135", "FMT_120"]),
            favoriteProcesses: expect.arrayContaining(["C41", "ECN2"]),
            favoriteCameras: ["Leica M6", "Hasselblad 500C/M"],
            shootingLevel: "ADVANCED",
          }),
        })
      );
      expect(useAuthStore.getState().user?.fullName).toBe("Nguyễn Quang Minh (Analog Pro)");
    });
  });

  it("manages AddressBook addresses, selects GPS coordinates and creates/deletes address", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue({
      data: {
        success: true,
        code: "SUCCESS",
        message: "Lấy danh sách sổ địa chỉ thành công.",
        data: [
          {
            id: "addr-001",
            userId: "0192e8f0-3b4a-7c11-8a22-3f4e5a6b7c8e",
            recipientName: "Nguyễn Quang Minh",
            recipientPhone: "0908123456",
            fullAddress: "Số 42 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
            wardCode: "26734",
            districtCode: "760",
            provinceCode: "79",
            latitude: 10.774562,
            longitude: 106.703418,
            isDefault: true,
          },
        ],
      },
    });

    const postSpy = vi.spyOn(httpClient, "post").mockResolvedValueOnce({
      data: {
        success: true,
        code: "SUCCESS",
        message: "Thêm địa chỉ mới thành công.",
        data: {
          id: "addr-002",
          userId: "0192e8f0-3b4a-7c11-8a22-3f4e5a6b7c8e",
          recipientName: "Studio Minh Quận 3",
          recipientPhone: "0912345678",
          fullAddress: "Số 18B Phạm Ngọc Thạch, Phường Võ Thị Sáu, Quận 3, TP. Hồ Chí Minh",
          wardCode: "27127",
          districtCode: "770",
          provinceCode: "79",
          latitude: 10.78437,
          longitude: 106.684409,
          isDefault: true,
        },
      },
    });

    const deleteSpy = vi.spyOn(httpClient, "delete").mockResolvedValueOnce({
      data: {
        success: true,
        code: "SUCCESS",
        message: "Đã xóa địa chỉ khỏi sổ địa chỉ.",
        data: {
          deletedAddressId: "addr-001",
          newDefaultAddressId: "addr-002",
          deletedAt: "2026-11-08T12:10:00Z",
        },
      },
    });

    renderWithQueryClient(<AddressBook />);

    await waitFor(() => {
      expect(
        screen.getByText(/Số 42 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh/i)
      ).toBeTruthy();
    });

    // Mở form thêm địa chỉ mới
    fireEvent.click(screen.getByRole("button", { name: /Thêm địa chỉ mới/i }));

    fireEvent.change(screen.getByLabelText(/Tên người nhận/i), {
      target: { value: "Studio Minh Quận 3" },
    });
    fireEvent.change(screen.getByLabelText(/Số điện thoại người nhận/i), {
      target: { value: "0912345678" },
    });
    fireEvent.change(screen.getByLabelText(/Tỉnh \/ Thành phố/i), {
      target: { value: "79" },
    });
    fireEvent.change(screen.getByLabelText(/Quận \/ Huyện/i), {
      target: { value: "770" },
    });
    fireEvent.change(screen.getByLabelText(/Mã Phường \/ Xã/i), {
      target: { value: "27127" },
    });
    fireEvent.change(screen.getByLabelText(/Địa chỉ chi tiết/i), {
      target: {
        value: "Số 18B Phạm Ngọc Thạch, Phường Võ Thị Sáu, Quận 3, TP. Hồ Chí Minh",
      },
    });

    // Kiểm tra tọa độ vĩ độ/kinh độ đã tự động điền theo Quận 3 (10.78437, 106.684409) hoặc cho phép chỉnh sửa
    fireEvent.change(screen.getByLabelText(/Vĩ độ \(Latitude\)/i), {
      target: { value: "10.78437" },
    });
    fireEvent.change(screen.getByLabelText(/Kinh độ \(Longitude\)/i), {
      target: { value: "106.684409" },
    });

    fireEvent.click(screen.getByLabelText(/Đặt làm địa chỉ mặc định/i));
    fireEvent.click(screen.getByRole("button", { name: /Lưu địa chỉ/i }));

    await waitFor(() => {
      expect(postSpy).toHaveBeenCalledWith(
        "/api/v1/users/me/addresses",
        expect.objectContaining({
          recipientName: "Studio Minh Quận 3",
          recipientPhone: "0912345678",
          fullAddress: "Số 18B Phạm Ngọc Thạch, Phường Võ Thị Sáu, Quận 3, TP. Hồ Chí Minh",
          provinceCode: "79",
          districtCode: "770",
          wardCode: "27127",
          latitude: 10.78437,
          longitude: 106.684409,
          isDefault: true,
        })
      );
    });

    // Xóa địa chỉ hiện có
    fireEvent.click(screen.getByRole("button", { name: /Xóa địa chỉ Nguyễn Quang Minh/i }));
    await waitFor(() => {
      expect(deleteSpy).toHaveBeenCalledWith("/api/v1/users/me/addresses/addr-001");
    });
  });

  it("submits EXPERT role application with portfolio links to Backend via RoleApplicationModal", async () => {
    const postSpy = vi.spyOn(httpClient, "post").mockResolvedValueOnce({
      data: {
        success: true,
        code: "SUCCESS",
        message:
          "Nộp hồ sơ đăng ký nâng cấp vai trò thành công. Ban quản trị sẽ xét duyệt trong vòng 24-48 giờ.",
        data: {
          id: "role-app-expert-001",
          userId: "0192e8f0-3b4a-7c11-8a22-3f4e5a6b7c8e",
          requestedRole: "EXPERT",
          status: "PENDING",
          portfolioPayload: {
            specialtyAreas: ["DARKROOM_PRINTING", "C41_CHEMISTRY"],
            yearsOfExperience: 6,
            portfolioLinks: [
              "https://flickr.com/photos/minh-analog",
              "https://behance.net/minh-darkroom",
            ],
            sampleArticleSummary:
              "Hướng dẫn kiểm soát nhiệt độ thuốc tráng C-41 và push/pull Kodak Vision3.",
          },
          reviewedByAdminId: null,
          reviewNote: null,
          reviewedAt: null,
          createdAt: "2026-11-08T12:30:00Z",
        },
      },
    });

    const onSuccess = vi.fn();
    const onClose = vi.fn();

    renderWithQueryClient(
      <RoleApplicationModal isOpen={true} onClose={onClose} onSuccess={onSuccess} />
    );

    // Mặc định chọn vai trò Chuyên gia (EXPERT)
    fireEvent.change(screen.getByLabelText(/Số năm kinh nghiệm/i), {
      target: { value: "6" },
    });
    fireEvent.change(screen.getByLabelText(/Link Portfolio/i), {
      target: {
        value: "https://flickr.com/photos/minh-analog, https://behance.net/minh-darkroom",
      },
    });
    fireEvent.change(screen.getByLabelText(/Tóm tắt bài viết mẫu \/ Kinh nghiệm chuyên môn/i), {
      target: {
        value: "Hướng dẫn kiểm soát nhiệt độ thuốc tráng C-41 và push/pull Kodak Vision3.",
      },
    });

    fireEvent.click(screen.getByRole("button", { name: /Gửi hồ sơ xét duyệt/i }));

    await waitFor(() => {
      expect(postSpy).toHaveBeenCalledWith(
        "/api/v1/role-applications",
        expect.objectContaining({
          requestedRole: "EXPERT",
          portfolioPayload: expect.objectContaining({
            yearsOfExperience: 6,
            portfolioLinks: [
              "https://flickr.com/photos/minh-analog",
              "https://behance.net/minh-darkroom",
            ],
            sampleArticleSummary:
              "Hướng dẫn kiểm soát nhiệt độ thuốc tráng C-41 và push/pull Kodak Vision3.",
          }),
        })
      );
    });

    expect(onSuccess).toHaveBeenCalled();
  });

  it("uploads avatar via 1 single API POST /api/v1/users/me/avatar when clicking the camera icon on avatar and updates image immediately", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue({
      data: {
        success: true,
        code: "SUCCESS",
        message: "OK",
        data: {
          id: "0192e8f0-3b4a-7c11-8a22-3f4e5a6b7c8e",
          email: "minh.nguyen@analoghub.vn",
          phoneNumber: "0908123456",
          fullName: "Nguyễn Quang Minh",
          avatarKey: null,
          avatarUrl: null,
          accountStatus: "ACTIVE",
          authProvider: "LOCAL",
          roles: ["CUSTOMER"],
          labMemberships: [],
          stylePreferences: {
            favoriteFormats: ["FMT_135"],
            favoriteProcesses: ["C41"],
            preferredColorTones: ["WARM_PASTEL"],
            favoriteCameras: ["Leica M6"],
            shootingLevel: "INTERMEDIATE",
          },
          createdAt: "2026-11-08T10:00:00Z",
          updatedAt: "2026-11-08T10:00:00Z",
        },
      },
    });

    const uploadPostSpy = vi.spyOn(httpClient, "post").mockResolvedValueOnce({
      data: {
        success: true,
        code: "SUCCESS",
        message: "Cập nhật ảnh đại diện thành công.",
        data: {
          id: "0192e8f0-3b4a-7c11-8a22-3f4e5a6b7c8e",
          email: "minh.nguyen@analoghub.vn",
          phoneNumber: "0908123456",
          fullName: "Nguyễn Quang Minh",
          avatarKey: "public/avatars/0192e8f0-3b4a-7c11-8a22-3f4e5a6b7c8e/avatar_new.webp",
          avatarUrl:
            "http://localhost:9000/analoghub-media/public/avatars/0192e8f0-3b4a-7c11-8a22-3f4e5a6b7c8e/avatar_new.webp",
          accountStatus: "ACTIVE",
          authProvider: "LOCAL",
          roles: ["CUSTOMER"],
          labMemberships: [],
          stylePreferences: {
            favoriteFormats: ["FMT_135"],
            favoriteProcesses: ["C41"],
            preferredColorTones: ["WARM_PASTEL"],
            favoriteCameras: ["Leica M6"],
            shootingLevel: "INTERMEDIATE",
          },
          createdAt: "2026-11-08T10:00:00Z",
          updatedAt: "2026-11-08T13:00:00Z",
        },
      },
    });

    renderWithQueryClient(<UserProfile />);

    await waitFor(() => {
      expect(screen.getByDisplayValue("Nguyễn Quang Minh")).toBeTruthy();
    });

    // Đảm bảo không còn ô nhập Khóa ảnh đại diện (avatarKey)
    expect(screen.queryByLabelText(/Khóa ảnh đại diện/i)).toBeNull();

    // Có nút máy ảnh trên avatar
    expect(screen.getByRole("button", { name: /Cập nhật ảnh đại diện/i })).toBeTruthy();

    const fileInput = screen.getByTestId("avatar-file-input") as HTMLInputElement;
    const mockFile = new File(["webp-binary-data"], "avatar_new.webp", {
      type: "image/webp",
    });

    fireEvent.change(fileInput, { target: { files: [mockFile] } });

    // Modal Căn Chỉnh & Cắt Ảnh Đại Diện hiển thị để chọn vùng ảnh
    await waitFor(() => {
      expect(screen.getByText(/Căn Chỉnh & Cắt Ảnh Đại Diện/i)).toBeTruthy();
    });

    fireEvent.change(screen.getByLabelText(/Thu phóng ảnh đại diện/i), {
      target: { value: "1.5" },
    });
    fireEvent.change(screen.getByLabelText(/Căn ngang ảnh đại diện/i), {
      target: { value: "25" },
    });
    fireEvent.change(screen.getByLabelText(/Căn dọc ảnh đại diện/i), {
      target: { value: "-15" },
    });

    fireEvent.click(screen.getByTestId("confirm-crop-avatar-btn"));

    await waitFor(() => {
      expect(uploadPostSpy).toHaveBeenCalledWith(
        "/api/v1/users/me/avatar",
        expect.any(FormData),
        expect.objectContaining({
          headers: { "Content-Type": "multipart/form-data" },
        })
      );
      const avatarImg = screen.getByTestId("user-avatar-img") as HTMLImageElement;
      expect(avatarImg.src).toContain(
        "http://localhost:9000/analoghub-media/public/avatars/0192e8f0-3b4a-7c11-8a22-3f4e5a6b7c8e/avatar_new.webp"
      );
      expect(useAuthStore.getState().user?.avatarUrl).toBe(
        "http://localhost:9000/analoghub-media/public/avatars/0192e8f0-3b4a-7c11-8a22-3f4e5a6b7c8e/avatar_new.webp"
      );
    });
  });

  it("validates strong password criteria in ChangePasswordCard and submits POST /api/v1/auth/change-password", async () => {
    const postSpy = vi.spyOn(httpClient, "post").mockResolvedValueOnce({
      data: {
        success: true,
        code: "SUCCESS",
        message: "Đổi mật khẩu thành công.",
        data: {
          revokedSessionsCount: 3,
          changedAt: "2026-11-10T03:00:00Z",
        },
      },
    });

    renderWithQueryClient(<ChangePasswordCard />);

    fireEvent.change(screen.getByLabelText(/Mật khẩu hiện tại/i), {
      target: { value: "OldPass@2025" },
    });
    fireEvent.change(screen.getByLabelText(/^Mật khẩu mới/i), {
      target: { value: "NewPass@2026" },
    });
    fireEvent.change(screen.getByLabelText(/Xác nhận mật khẩu mới/i), {
      target: { value: "NewPass@2026" },
    });

    expect(screen.getByText(/Đạt 4\/4 tiêu chuẩn/i)).toBeTruthy();

    fireEvent.click(
      screen.getByRole("button", { name: /Cập nhật mật khẩu & Thu hồi phiên cũ/i })
    );

    await waitFor(() => {
      expect(postSpy).toHaveBeenCalledWith("/api/v1/auth/change-password", {
        currentPassword: "OldPass@2025",
        newPassword: "NewPass@2026",
        confirmNewPassword: "NewPass@2026",
      });
      expect(screen.getByText(/Đã tự động thu hồi 3 phiên đăng nhập cũ/i)).toBeTruthy();
    });
  });
});
