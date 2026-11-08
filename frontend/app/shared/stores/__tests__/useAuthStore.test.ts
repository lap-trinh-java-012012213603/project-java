import { beforeEach, describe, expect, it } from "vitest";
import { useAuthStore, type AuthUser } from "@/shared/stores/useAuthStore";

const sampleUser: AuthUser = {
  id: "0192e8f0-3b4a-7c11-8a22-3f4e5a6b7c8e",
  email: "minh.nguyen@analoghub.vn",
  fullName: "Nguyễn Quang Minh",
  avatarUrl: "https://cdn.analoghub.vn/avatars/minh.webp",
  roles: ["CUSTOMER", "LAB_OWNER"],
  labIds: ["lab-hanoi-01", "lab-saigon-02"],
  activeLabId: "lab-hanoi-01",
};

describe("FE-C02 useAuthStore (Zustand + localStorage)", () => {
  beforeEach(() => {
    window.localStorage.clear();
    useAuthStore.getState().logout();
  });

  it("persists auth session into localStorage under analoghub_auth_session", () => {
    useAuthStore.getState().setAuth("jwt-access-01", sampleUser, "rft-token-01");

    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(true);
    expect(state.accessToken).toBe("jwt-access-01");
    expect(state.refreshToken).toBe("rft-token-01");
    expect(state.user?.email).toBe("minh.nguyen@analoghub.vn");

    const rawPersisted = window.localStorage.getItem("analoghub_auth_session");
    expect(rawPersisted).not.toBeNull();
    const parsed = JSON.parse(rawPersisted!);
    expect(parsed.state.accessToken).toBe("jwt-access-01");
    expect(parsed.state.refreshToken).toBe("rft-token-01");
    expect(parsed.state.isAuthenticated).toBe(true);
    expect(parsed.state.user.id).toBe(sampleUser.id);
  });

  it("checks single and multiple RBAC roles accurately via hasRole", () => {
    useAuthStore.getState().setAuth("jwt-access-01", sampleUser);

    expect(useAuthStore.getState().hasRole("CUSTOMER")).toBe(true);
    expect(useAuthStore.getState().hasRole("LAB_OWNER")).toBe(true);
    expect(useAuthStore.getState().hasRole("SUPER_ADMIN")).toBe(false);
    expect(useAuthStore.getState().hasRole(["SHIPPER", "LAB_OWNER"])).toBe(true);
    expect(useAuthStore.getState().hasRole(["SHIPPER", "SUPER_ADMIN"])).toBe(false);
  });

  it("enforces multi-tenant lab isolation via hasLabAccess and allows SUPER_ADMIN", () => {
    useAuthStore.getState().setAuth("jwt-access-01", sampleUser);

    expect(useAuthStore.getState().hasLabAccess("lab-hanoi-01")).toBe(true);
    expect(useAuthStore.getState().hasLabAccess("lab-other-99")).toBe(false);

    useAuthStore.getState().setActiveLabId("lab-saigon-02");
    expect(useAuthStore.getState().user?.activeLabId).toBe("lab-saigon-02");

    useAuthStore.getState().setAuth("jwt-admin", {
      ...sampleUser,
      roles: ["SUPER_ADMIN"],
      labIds: [],
    });
    expect(useAuthStore.getState().hasLabAccess("lab-other-99")).toBe(true);
  });

  it("clears tokens, user and authentication flag on logout", () => {
    useAuthStore.getState().setAuth("jwt-access-01", sampleUser, "rft-token-01");
    useAuthStore.getState().logout();

    const state = useAuthStore.getState();
    expect(state.accessToken).toBeNull();
    expect(state.refreshToken).toBeNull();
    expect(state.user).toBeNull();
    expect(state.isAuthenticated).toBe(false);
  });
});
