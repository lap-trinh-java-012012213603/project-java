import axios, { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from "axios";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiErrorResponse, ApiResponse } from "@/shared/api/apiEnvelope";
import { parseApiError } from "@/shared/api/handleApiError";
import { httpClient } from "@/shared/api/httpClient";
import { useAuthStore, type AuthUser } from "@/shared/stores/useAuthStore";
import { generateUuidV7, isUuidV7 } from "@/shared/utils/uuid";

const UUID_V7_REGEX =
  /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

const mockUser: AuthUser = {
  id: "0192e8f0-3b4a-7c11-8a22-3f4e5a6b7c8e",
  email: "ktv@analoghub.vn",
  fullName: "Trần Kỹ Thuật",
  roles: ["LAB_STAFF"],
  labIds: ["lab-01"],
  activeLabId: "lab-01",
};

function createAxios401Error(
  config: InternalAxiosRequestConfig,
  code = "TOKEN_EXPIRED"
): AxiosError<ApiErrorResponse> {
  const errorBody: ApiErrorResponse = {
    success: false,
    code,
    errorCode: code,
    message: "Access token đã hết hạn.",
    path: config.url,
    timestamp: new Date().toISOString(),
  };

  return new AxiosError<ApiErrorResponse>(
    "Request failed with status code 401",
    "ERR_BAD_REQUEST",
    config,
    {},
    {
      data: errorBody,
      status: 401,
      statusText: "Unauthorized",
      headers: {},
      config,
    }
  );
}

describe("FE-C02 httpClient, UUIDv7 Idempotency-Key & Single-Flight Refresh Mutex", () => {
  const originalAdapter = httpClient.defaults.adapter;

  beforeEach(() => {
    window.localStorage.clear();
    useAuthStore.getState().logout();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    httpClient.defaults.adapter = originalAdapter;
  });

  it("generates valid time-ordered RFC 9562 UUIDv7 strings", () => {
    const id1 = generateUuidV7();
    const id2 = generateUuidV7();

    expect(id1).toMatch(UUID_V7_REGEX);
    expect(id2).toMatch(UUID_V7_REGEX);
    expect(isUuidV7(id1)).toBe(true);
    expect(id1).not.toBe(id2);
  });

  it("attaches Bearer token and UUIDv7 Idempotency-Key on mutating requests while respecting custom keys", async () => {
    useAuthStore.getState().setAuth("valid-jwt-token", mockUser, "rft-valid-01");

    const capturedConfigs: InternalAxiosRequestConfig[] = [];
    httpClient.defaults.adapter = async (config) => {
      capturedConfigs.push(config);
      return {
        data: { success: true, code: "SUCCESS", message: "OK", data: { ok: true } },
        status: 200,
        statusText: "OK",
        headers: {},
        config,
      };
    };

    await httpClient.get("/api/v1/labs");
    await httpClient.post("/api/v1/bookings/orders", { labId: "lab-01" });
    await httpClient.post(
      "/api/v1/payments/checkout-url",
      { orderId: "ord-01" },
      { headers: { "Idempotency-Key": "custom-client-key-001" } }
    );

    expect(capturedConfigs).toHaveLength(3);

    // 1. GET: có Bearer token, không sinh Idempotency-Key
    const getHeaders = AxiosHeaders.from(capturedConfigs[0].headers);
    expect(getHeaders.get("Authorization")).toBe("Bearer valid-jwt-token");
    expect(getHeaders.get("Idempotency-Key")).toBeUndefined();

    // 2. POST: tự động gắn Bearer token + Idempotency-Key chuẩn UUIDv7
    const postHeaders = AxiosHeaders.from(capturedConfigs[1].headers);
    expect(postHeaders.get("Authorization")).toBe("Bearer valid-jwt-token");
    const generatedKey = String(postHeaders.get("Idempotency-Key"));
    expect(generatedKey).toMatch(UUID_V7_REGEX);

    // 3. POST có sẵn Idempotency-Key: giữ nguyên key truyền vào
    const customHeaders = AxiosHeaders.from(capturedConfigs[2].headers);
    expect(customHeaders.get("Idempotency-Key")).toBe("custom-client-key-001");
  });

  it("fires ONLY 1 refresh request when 5 concurrent requests fail with 401 TOKEN_EXPIRED and replays all 5", async () => {
    useAuthStore.getState().setAuth("expired-access-token", mockUser, "rft-initial-token");

    const postIdempotencyKeysByAttempt: string[] = [];
    let adapterCallCount = 0;

    httpClient.defaults.adapter = async (config) => {
      adapterCallCount += 1;
      const headers = AxiosHeaders.from(config.headers);
      const authHeader = headers.get("Authorization");

      if (config.url === "/api/v1/bookings/orders") {
        postIdempotencyKeysByAttempt.push(String(headers.get("Idempotency-Key")));
      }

      if (authHeader === "Bearer expired-access-token") {
        throw createAxios401Error(config, "TOKEN_EXPIRED");
      }

      if (authHeader === "Bearer fresh-access-token-v2") {
        return {
          data: {
            success: true,
            code: "SUCCESS",
            message: "OK",
            data: { url: config.url, authUsed: authHeader },
          },
          status: 200,
          statusText: "OK",
          headers: {},
          config,
        };
      }

      throw new Error(`Unexpected Authorization header: ${String(authHeader)}`);
    };

    const refreshSpy = vi.spyOn(axios, "post").mockImplementation(async (_url, _body, reqConfig) => {
      // Giả lập độ trễ mạng 25ms khi gọi /api/v1/auth/refresh để đảm bảo 5 requests chạm mutex đồng thời
      await new Promise((resolve) => setTimeout(resolve, 25));

      const refreshHeaders = AxiosHeaders.from(reqConfig?.headers as never);
      expect(String(refreshHeaders.get("Idempotency-Key"))).toMatch(UUID_V7_REGEX);

      return {
        data: {
          success: true,
          code: "SUCCESS",
          message: "Làm mới phiên đăng nhập thành công.",
          data: {
            accessToken: "fresh-access-token-v2",
            refreshToken: "rft-rotated-token-v2",
          },
        } satisfies ApiResponse<{ accessToken: string; refreshToken: string }>,
        status: 200,
        statusText: "OK",
        headers: {},
        config: {} as InternalAxiosRequestConfig,
      };
    });

    const endpoints = [
      () => httpClient.get<ApiResponse<{ url: string; authUsed: string }>>("/api/v1/my-labs/lab-01/kanban"),
      () => httpClient.get<ApiResponse<{ url: string; authUsed: string }>>("/api/v1/archive/rolls"),
      () => httpClient.post<ApiResponse<{ url: string; authUsed: string }>>("/api/v1/bookings/orders", { items: [] }),
      () => httpClient.get<ApiResponse<{ url: string; authUsed: string }>>("/api/v1/messaging/conversations"),
      () => httpClient.patch<ApiResponse<{ url: string; authUsed: string }>>("/api/v1/marketplace/orders/ord-9", {}),
    ];

    const responses = await Promise.all(endpoints.map((fn) => fn()));

    // Tiêu chí bắt buộc FE-C02: 5 request đồng thời bị 401 chỉ sinh ĐÚNG 1 refresh request
    expect(refreshSpy).toHaveBeenCalledTimes(1);
    expect(refreshSpy.mock.calls[0][0]).toContain("/api/v1/auth/refresh");
    expect(refreshSpy.mock.calls[0][1]).toEqual({ refreshToken: "rft-initial-token" });

    // Tổng cộng adapter nhận 5 lần lỗi 401 ban đầu + 5 lần replay thành công = 10 lần
    expect(adapterCallCount).toBe(10);

    // Toàn bộ 5 request đều được replay thành công với token mới
    expect(responses).toHaveLength(5);
    for (const res of responses) {
      expect(res.status).toBe(200);
      expect(res.data.data.authUsed).toBe("Bearer fresh-access-token-v2");
    }

    // Request POST giữ nguyên cùng 1 Idempotency-Key giữa lần gọi đầu và lần replay
    expect(postIdempotencyKeysByAttempt).toHaveLength(2);
    expect(postIdempotencyKeysByAttempt[0]).toMatch(UUID_V7_REGEX);
    expect(postIdempotencyKeysByAttempt[1]).toBe(postIdempotencyKeysByAttempt[0]);

    // Store đã được cập nhật cặp token mới sau khi xoay vòng (Token Family Rotation)
    expect(useAuthStore.getState().accessToken).toBe("fresh-access-token-v2");
    expect(useAuthStore.getState().refreshToken).toBe("rft-rotated-token-v2");
  });

  it("logs out user and rejects all queued requests when refresh token fails", async () => {
    useAuthStore.getState().setAuth("expired-access-token", mockUser, "rft-revoked-token");

    httpClient.defaults.adapter = async (config) => {
      throw createAxios401Error(config, "TOKEN_EXPIRED");
    };

    const refreshFailure = new AxiosError("Refresh failed", "ERR_BAD_REQUEST");
    const refreshSpy = vi.spyOn(axios, "post").mockImplementation(async () => {
      await new Promise((resolve) => setTimeout(resolve, 15));
      throw refreshFailure;
    });

    const results = await Promise.allSettled([
      httpClient.get("/api/v1/archive/rolls"),
      httpClient.get("/api/v1/my-labs/lab-01/kanban"),
      httpClient.get("/api/v1/messaging/conversations"),
    ]);

    expect(refreshSpy).toHaveBeenCalledTimes(1);
    expect(results.every((r) => r.status === "rejected")).toBe(true);
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(useAuthStore.getState().accessToken).toBeNull();
  });

  it("does not trigger refresh loop when /auth/login itself returns 401", async () => {
    httpClient.defaults.adapter = async (config) => {
      throw createAxios401Error(config, "IAM_INVALID_CREDENTIALS");
    };

    const refreshSpy = vi.spyOn(axios, "post");

    await expect(
      httpClient.post("/api/v1/auth/login", { email: "a@b.com", password: "wrong" })
    ).rejects.toBeInstanceOf(AxiosError);

    expect(refreshSpy).not.toHaveBeenCalled();
  });

  it("maps backend error codes to user-friendly Vietnamese messages via parseApiError", () => {
    const cfg = { headers: new AxiosHeaders(), url: "/api/v1/labs" } as InternalAxiosRequestConfig;
    const err = createAxios401Error(cfg, "TOKEN_REUSE_DETECTED");
    expect(parseApiError(err)).toContain("Cảnh báo bảo mật");
  });
});
