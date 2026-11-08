import axios, {
  AxiosError,
  AxiosHeaders,
  type InternalAxiosRequestConfig,
} from "axios";
import { useAuthStore } from "../stores/useAuthStore";
import { generateUuidV7 } from "../utils/uuid";
import type { ApiErrorResponse, ApiResponse } from "./apiEnvelope";

const BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

const MUTATING_HTTP_METHODS = new Set(["post", "put", "patch", "delete"]);

const AUTH_BYPASS_PATHS = [
  "/auth/login",
  "/auth/register",
  "/auth/oauth2/google",
  "/auth/refresh",
  "/auth/logout",
];

interface RefreshTokenPayload {
  accessToken: string;
  refreshToken?: string;
}

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

export const httpClient = axios.create({
  baseURL: BASE_URL,
  timeout: 20000,
  headers: {
    "Content-Type": "application/json",
  },
  withCredentials: true, // Cho phép trình duyệt gửi Cookie chứa refreshToken (nếu dùng HttpOnly Cookie)
});

// 1. Request Interceptor: Tự động đính kèm Bearer Token và Idempotency-Key chuẩn UUIDv7
httpClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const headers = AxiosHeaders.from(config.headers);

    const token = useAuthStore.getState().accessToken;
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }

    const method = config.method?.toLowerCase() || "get";
    if (MUTATING_HTTP_METHODS.has(method)) {
      if (!headers.get("Idempotency-Key") && !headers.get("X-Idempotency-Key")) {
        headers.set("Idempotency-Key", generateUuidV7());
      }
    }

    config.headers = headers;
    return config;
  },
  (error) => Promise.reject(error)
);

// 2. Single-Flight Refresh Token Mutex Queue
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (err: unknown) => void;
}> = [];

const processQueue = (error: unknown, token: string | null = null): void => {
  failedQueue.forEach((pending) => {
    if (error) {
      pending.reject(error);
    } else if (token) {
      pending.resolve(token);
    }
  });
  failedQueue = [];
};

function isExcludedAuthEndpoint(url?: string): boolean {
  if (!url) return false;
  return AUTH_BYPASS_PATHS.some((path) => url.includes(path));
}

function redirectToLoginOnSessionExpired(): void {
  if (typeof window === "undefined") return;
  if (typeof navigator !== "undefined" && /jsdom/i.test(navigator.userAgent)) {
    return;
  }
  if (!window.location.pathname.startsWith("/auth/login")) {
    const redirectTarget = encodeURIComponent(
      `${window.location.pathname}${window.location.search}`
    );
    window.location.assign(`/auth/login?redirect=${redirectTarget}`);
  }
}

// 3. Response Interceptor: Bắt lỗi 401 TOKEN_EXPIRED, gọi /api/v1/auth/refresh 1 lần duy nhất và replay requests
httpClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorResponse>) => {
    const originalRequest = error.config as RetryableRequestConfig | undefined;
    if (!originalRequest) {
      return Promise.reject(error);
    }

    if (
      error.response?.status === 401 &&
      !originalRequest._retry &&
      !isExcludedAuthEndpoint(originalRequest.url)
    ) {
      if (isRefreshing) {
        return new Promise<string>((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then((newToken) => {
          const replayHeaders = AxiosHeaders.from(originalRequest.headers);
          replayHeaders.set("Authorization", `Bearer ${newToken}`);
          originalRequest.headers = replayHeaders;
          return httpClient(originalRequest);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const storedRefreshToken = useAuthStore.getState().refreshToken;
        const refreshBody = storedRefreshToken ? { refreshToken: storedRefreshToken } : {};

        const { data } = await axios.post<ApiResponse<RefreshTokenPayload>>(
          `${BASE_URL}/api/v1/auth/refresh`,
          refreshBody,
          {
            withCredentials: true,
            headers: {
              "Content-Type": "application/json",
              "Idempotency-Key": generateUuidV7(),
            },
          }
        );

        const newAccessToken = data.data.accessToken;
        const rotatedRefreshToken = data.data.refreshToken;

        if (rotatedRefreshToken !== undefined) {
          useAuthStore.getState().setTokens(newAccessToken, rotatedRefreshToken);
        } else {
          useAuthStore.getState().setAccessToken(newAccessToken);
        }

        processQueue(null, newAccessToken);

        const replayHeaders = AxiosHeaders.from(originalRequest.headers);
        replayHeaders.set("Authorization", `Bearer ${newAccessToken}`);
        originalRequest.headers = replayHeaders;

        return httpClient(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        useAuthStore.getState().logout();
        redirectToLoginOnSessionExpired();
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);
