import type { ApiResponse } from "@/shared/api/apiEnvelope";
import { httpClient } from "@/shared/api/httpClient";
import { type AuthUser, useAuthStore } from "@/shared/stores/useAuthStore";
import type {
  AuthTokenResponse,
  AuthUserDto,
  GoogleOAuth2LoginRequest,
  LoginRequest,
  LogoutRequest,
  LogoutResponse,
  RegisterUserRequest,
} from "../types/iam.types";

export function mapBackendUserToAuthUser(dto: AuthUserDto): AuthUser {
  const membershipLabIds = (dto.labMemberships ?? [])
    .map((membership) => (membership.labId ? String(membership.labId) : ""))
    .filter(Boolean);

  const labIds = dto.labIds && dto.labIds.length > 0 ? dto.labIds : membershipLabIds;

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
};
