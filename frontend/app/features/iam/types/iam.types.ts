import type { UserRole } from "@/shared/stores/useAuthStore";

export type AccountStatus = "ACTIVE" | "SUSPENDED" | "BANNED";
export type AuthProvider = "LOCAL" | "GOOGLE";
export type ShootingLevel = "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "PROFESSIONAL";
export type RoleUpgradeCode = "EXPERT" | "LAB_OWNER" | "DELIVERY_PARTNER";
export type RoleApplicationStatus = "PENDING" | "APPROVED" | "REJECTED";

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

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  confirmNewPassword: string;
}

export interface ChangePasswordResponse {
  revokedSessionsCount?: number;
  changedAt?: string;
}

export interface UserProfileDto {
  id: string;
  email: string;
  phoneNumber?: string | null;
  fullName: string;
  avatarKey?: string | null;
  avatarUrl?: string | null;
  accountStatus: AccountStatus;
  authProvider: AuthProvider;
  roles: UserRole[];
  labMemberships?: LabMembershipDto[];
  stylePreferences?: StylePreferencesDto | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface UpdateUserProfileRequest {
  fullName: string;
  phoneNumber: string;
  avatarKey?: string | null;
  stylePreferences?: StylePreferencesDto;
}

export interface PresignAvatarUploadRequest {
  fileName: string;
  contentType: string;
  fileSizeBytes: number;
}

export interface PresignedAvatarUploadResponse {
  objectKey: string;
  uploadPutUrl: string;
  publicPreviewUrl?: string | null;
  expiresInSeconds: number;
}

export interface UserAddressDto {
  id: string;
  userId?: string;
  recipientName: string;
  recipientPhone: string;
  fullAddress: string;
  wardCode: string;
  districtCode: string;
  provinceCode: string;
  latitude: number;
  longitude: number;
  isDefault: boolean;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface UpsertUserAddressRequest {
  recipientName: string;
  recipientPhone: string;
  fullAddress: string;
  wardCode: string;
  districtCode: string;
  provinceCode: string;
  latitude?: number | null;
  longitude?: number | null;
  isDefault: boolean;
}

export interface DeleteUserAddressResponse {
  deletedAddressId: string;
  newDefaultAddressId?: string | null;
  deletedAt: string;
}

export interface SubmitRoleApplicationRequest {
  requestedRole: RoleUpgradeCode;
  portfolioPayload: Record<string, unknown>;
}

export interface RoleApplicationDto {
  id: string;
  userId: string;
  requestedRole: RoleUpgradeCode;
  portfolioPayload: Record<string, unknown>;
  status: RoleApplicationStatus;
  reviewedByAdminId?: string | null;
  reviewNote?: string | null;
  reviewedAt?: string | null;
  createdAt: string;
}
