import type { UserRole } from "@/shared/stores/useAuthStore";

export type AccountStatus = "ACTIVE" | "SUSPENDED" | "BANNED";
export type AuthProvider = "LOCAL" | "GOOGLE";

export interface StylePreferencesDto {
  favoriteFormats?: string[];
  favoriteProcesses?: string[];
  preferredColorTones?: string[];
  favoriteCameras?: string[];
  shootingLevel?: string;
}

export interface LabMembershipDto {
  labId: string;
  memberRole?: string;
  [key: string]: unknown;
}

export interface AuthUserDto {
  id: string;
  email: string;
  phoneNumber?: string | null;
  fullName: string;
  avatarUrl?: string | null;
  accountStatus?: AccountStatus;
  authProvider?: AuthProvider;
  roles: UserRole[];
  labMemberships?: LabMembershipDto[];
  labIds?: string[];
  stylePreferences?: StylePreferencesDto | null;
  createdAt?: string;
}

export interface AuthTokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  refreshExpiresInSeconds?: number;
  isNewUser?: boolean;
  user: AuthUserDto;
}

export interface LoginRequest {
  email: string;
  password: string;
  deviceInfo?: string;
}

export interface RegisterUserRequest {
  email: string;
  phoneNumber: string;
  password: string;
  fullName: string;
  stylePreferences?: StylePreferencesDto;
}

export interface GoogleOAuth2LoginRequest {
  idToken: string;
  deviceInfo?: string;
}

export interface LogoutRequest {
  refreshToken?: string | null;
  logoutAllDevices?: boolean;
}

export interface LogoutResponse {
  revokedSessionsCount?: number;
}
