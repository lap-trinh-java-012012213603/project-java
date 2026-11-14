import { create } from "zustand";
import { createJSONStorage, persist, type StateStorage } from "zustand/middleware";

export type UserRole =
  | "CUSTOMER"
  | "LAB_STAFF"
  | "LAB_OWNER"
  | "SHIPPER"
  | "DELIVERY_PARTNER"
  | "EXPERT"
  | "ADMIN"
  | "SUPER_ADMIN";

export interface AuthUser {
  id: string;
  email: string;
  fullName: string;
  avatarUrl?: string;
  roles: UserRole[];
  labIds: string[]; // Danh sách các phòng lab mà user có quyền quản trị
  activeLabId?: string; // Phòng lab hiện đang chọn làm việc
}

export interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: AuthUser | null;
  isAuthenticated: boolean;
  setAuth: (token: string, user: AuthUser, refreshToken?: string | null) => void;
  setAccessToken: (token: string) => void;
  setTokens: (accessToken: string, refreshToken?: string | null) => void;
  setActiveLabId: (labId: string) => void;
  hasRole: (role: UserRole | UserRole[]) => boolean;
  hasLabAccess: (labId: string) => boolean;
  logout: () => void;
}

const ssrSafeLocalStorage: StateStorage = {
  getItem: (name: string) => {
    if (typeof window === "undefined" || !window.localStorage) return null;
    return window.localStorage.getItem(name);
  },
  setItem: (name: string, value: string) => {
    if (typeof window === "undefined" || !window.localStorage) return;
    window.localStorage.setItem(name, value);
  },
  removeItem: (name: string) => {
    if (typeof window === "undefined" || !window.localStorage) return;
    window.localStorage.removeItem(name);
  },
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,

      setAuth: (accessToken, user, refreshToken = null) =>
        set({
          accessToken,
          refreshToken,
          user,
          isAuthenticated: true,
        }),

      setAccessToken: (accessToken) =>
        set({
          accessToken,
          isAuthenticated: true,
        }),

      setTokens: (accessToken, refreshToken) =>
        set((state) => ({
          accessToken,
          refreshToken: refreshToken !== undefined ? refreshToken : state.refreshToken,
          isAuthenticated: true,
        })),

      setActiveLabId: (activeLabId) =>
        set((state) => ({
          user: state.user ? { ...state.user, activeLabId } : null,
        })),

      hasRole: (role) => {
        const userRoles = get().user?.roles || [];
        if (Array.isArray(role)) {
          return role.some((r) => userRoles.includes(r));
        }
        return userRoles.includes(role);
      },

      hasLabAccess: (labId) => {
        const { user } = get();
        if (!user) return false;
        if (user.roles.includes("SUPER_ADMIN") || user.roles.includes("ADMIN")) {
          return true;
        }
        return user.labIds.includes(labId);
      },

      logout: () =>
        set({
          accessToken: null,
          refreshToken: null,
          user: null,
          isAuthenticated: false,
        }),
    }),
    {
      name: "analoghub_auth_session",
      storage: createJSONStorage(() => ssrSafeLocalStorage),
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);
