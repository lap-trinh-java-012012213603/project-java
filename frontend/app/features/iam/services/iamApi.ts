import type { ApiResponse } from "@/shared/api/apiEnvelope";
import { httpClient } from "@/shared/api/httpClient";
import { type AuthUser, useAuthStore } from "@/shared/stores/useAuthStore";
import type {
  AuthTokenResponse,
  AuthUserDto,
  ChangePasswordRequest,
  ChangePasswordResponse,
  DeleteUserAddressResponse,
  GoogleOAuth2LoginRequest,
  LoginRequest,
  LogoutRequest,
  LogoutResponse,
  PresignAvatarUploadRequest,
  PresignedAvatarUploadResponse,
  RegisterUserRequest,
  RoleApplicationDto,
  RoleApplicationStatus,
  SubmitRoleApplicationRequest,
  UpdateUserProfileRequest,
  UpsertUserAddressRequest,
  UserAddressDto,
  UserProfileDto,
} from "../types/iam.types";

export function mapBackendUserToAuthUser(dto: AuthUserDto | UserProfileDto): AuthUser {
  const membershipLabIds = (dto.labMemberships ?? [])
    .map((membership) => (membership.labId ? String(membership.labId) : ""))
    .filter(Boolean);

  const rawLabIds = "labIds" in dto && dto.labIds && dto.labIds.length > 0 ? dto.labIds : [];
  const labIds = rawLabIds.length > 0 ? rawLabIds : membershipLabIds;

  return {
    id: dto.id,
    email: dto.email,
    fullName: dto.fullName,
    avatarUrl: dto.avatarUrl ?? undefined,
    roles: dto.roles ?? ["CUSTOMER"],
    labIds,
    activeLabId: labIds[0],
  };
}

export function persistAuthSession(tokenResponse: AuthTokenResponse): AuthUser {
  const authUser = mapBackendUserToAuthUser(tokenResponse.user);
  useAuthStore
    .getState()
    .setAuth(tokenResponse.accessToken, authUser, tokenResponse.refreshToken);
  return authUser;
}

export function syncProfileToAuthStore(profile: UserProfileDto): AuthUser {
  const currentUser = useAuthStore.getState().user;
  const mapped = mapBackendUserToAuthUser(profile);
  const mergedUser: AuthUser = {
    ...currentUser,
    ...mapped,
    labIds: mapped.labIds.length > 0 ? mapped.labIds : (currentUser?.labIds ?? []),
    activeLabId: mapped.activeLabId ?? currentUser?.activeLabId,
  };
  useAuthStore.setState({ user: mergedUser });
  return mergedUser;
}

export const iamApi = {
  async login(payload: LoginRequest): Promise<AuthTokenResponse> {
    const { data } = await httpClient.post<ApiResponse<AuthTokenResponse>>(
      "/api/v1/auth/login",
      payload
    );
    return data.data;
  },

  async register(payload: RegisterUserRequest): Promise<AuthTokenResponse> {
    const { data } = await httpClient.post<ApiResponse<AuthTokenResponse>>(
      "/api/v1/auth/register",
      payload
    );
    return data.data;
  },

  async loginWithGoogle(payload: GoogleOAuth2LoginRequest): Promise<AuthTokenResponse> {
    const { data } = await httpClient.post<ApiResponse<AuthTokenResponse>>(
      "/api/v1/auth/oauth2/google",
      payload
    );
    return data.data;
  },

  async logout(payload?: LogoutRequest): Promise<LogoutResponse> {
    const { data } = await httpClient.post<ApiResponse<LogoutResponse>>(
      "/api/v1/auth/logout",
      payload ?? {}
    );
    return data.data;
  },

  async changePassword(payload: ChangePasswordRequest): Promise<ChangePasswordResponse> {
    const { data } = await httpClient.post<ApiResponse<ChangePasswordResponse>>(
      "/api/v1/auth/change-password",
      payload
    );
    return data.data;
  },

  async getMyProfile(): Promise<UserProfileDto> {
    const { data } = await httpClient.get<ApiResponse<UserProfileDto>>("/api/v1/users/me");
    return data.data;
  },

  async uploadMyAvatar(file: File): Promise<UserProfileDto> {
    const formData = new FormData();
    formData.append("file", file);
    const { data } = await httpClient.post<ApiResponse<UserProfileDto>>(
      "/api/v1/users/me/avatar",
      formData,
      {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      }
    );
    syncProfileToAuthStore(data.data);
    return data.data;
  },

  async presignAvatarUpload(
    payload: PresignAvatarUploadRequest
  ): Promise<PresignedAvatarUploadResponse> {
    const { data } = await httpClient.post<ApiResponse<PresignedAvatarUploadResponse>>(
      "/api/v1/users/me/avatar/presign",
      payload
    );
    return data.data;
  },

  async uploadAvatarToPresignedUrl(uploadPutUrl: string, file: File): Promise<void> {
    const response = await fetch(uploadPutUrl, {
      method: "PUT",
      headers: {
        "Content-Type": file.type || "application/octet-stream",
      },
      body: file,
    });
    if (!response.ok) {
      throw new Error(`Tải ảnh đại diện lên kho lưu trữ thất bại (HTTP ${response.status}).`);
    }
  },

  async updateMyProfile(payload: UpdateUserProfileRequest): Promise<UserProfileDto> {
    const { data } = await httpClient.put<ApiResponse<UserProfileDto>>(
      "/api/v1/users/me",
      payload
    );
    syncProfileToAuthStore(data.data);
    return data.data;
  },

  async getMyAddresses(): Promise<UserAddressDto[]> {
    const { data } = await httpClient.get<ApiResponse<UserAddressDto[]>>(
      "/api/v1/users/me/addresses"
    );
    return data.data;
  },

  async createAddress(payload: UpsertUserAddressRequest): Promise<UserAddressDto> {
    const { data } = await httpClient.post<ApiResponse<UserAddressDto>>(
      "/api/v1/users/me/addresses",
      payload
    );
    return data.data;
  },

  async updateAddress(id: string, payload: UpsertUserAddressRequest): Promise<UserAddressDto> {
    const { data } = await httpClient.put<ApiResponse<UserAddressDto>>(
      `/api/v1/users/me/addresses/${id}`,
      payload
    );
    return data.data;
  },

  async deleteAddress(id: string): Promise<DeleteUserAddressResponse> {
    const { data } = await httpClient.delete<ApiResponse<DeleteUserAddressResponse>>(
      `/api/v1/users/me/addresses/${id}`
    );
    return data.data;
  },

  async submitRoleApplication(
    payload: SubmitRoleApplicationRequest
  ): Promise<RoleApplicationDto> {
    const { data } = await httpClient.post<ApiResponse<RoleApplicationDto>>(
      "/api/v1/role-applications",
      payload
    );
    return data.data;
  },

  async getMyRoleApplications(
    status?: RoleApplicationStatus
  ): Promise<RoleApplicationDto[]> {
    const { data } = await httpClient.get<ApiResponse<RoleApplicationDto[]>>(
      "/api/v1/role-applications/me",
      status ? { params: { status } } : undefined
    );
    return data.data;
  },
};
