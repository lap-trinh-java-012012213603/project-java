import { render, screen } from "@testing-library/react";
import { useQueryClient } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";
import { RootLayout } from "@/layouts/RootLayout";
import { createQueryClient, queryClient } from "@/shared/api/queryClient";
import { queryKeys } from "@/shared/api/queryKeys";

function QueryProbe() {
  const client = useQueryClient();
  const staleTime = client.getDefaultOptions().queries?.staleTime;
  return <div data-testid="query-probe">stale:{String(staleTime)}</div>;
}

describe("FE-C01 RootLayout & QueryClient setup", () => {
  it("wraps children with QueryClientProvider using @/* path alias", () => {
    const testClient = createQueryClient();

    render(
      <RootLayout client={testClient}>
        <QueryProbe />
      </RootLayout>
    );

    expect(screen.getByTestId("query-probe").textContent).toBe("stale:60000");
  });

  it("configures queryClient retry policy to skip 401/403/404 and retry transient errors under 2 attempts", () => {
    const createHttpError = (status: number) =>
      Object.assign(new Error(`HTTP ${status}`), { response: { status } });

    const retryFn = queryClient.getDefaultOptions().queries?.retry;
    expect(typeof retryFn).toBe("function");

    if (typeof retryFn === "function") {
      expect(retryFn(0, createHttpError(401))).toBe(false);
      expect(retryFn(0, createHttpError(403))).toBe(false);
      expect(retryFn(0, createHttpError(404))).toBe(false);
      expect(retryFn(0, createHttpError(500))).toBe(true);
      expect(retryFn(1, createHttpError(502))).toBe(true);
      expect(retryFn(2, createHttpError(502))).toBe(false);
    }

    expect(queryClient.getDefaultOptions().queries?.gcTime).toBe(600_000);
    expect(queryClient.getDefaultOptions().queries?.refetchOnWindowFocus).toBe(false);
    expect(queryClient.getDefaultOptions().queries?.refetchOnReconnect).toBe(true);
    expect(queryClient.getDefaultOptions().mutations?.retry).toBe(0);
  });

  it("provides hierarchical queryKeys for core AnalogHub domains", () => {
    expect(queryKeys.labs.detail("lab-01")).toEqual(["labs", "detail", "lab-01"]);
    expect(queryKeys.kanban.board("lab-01")).toEqual(["kanban", "board", "lab-01"]);
    expect(queryKeys.archive.roll("roll-01")).toEqual(["archive", "rolls", "roll-01"]);
    expect(queryKeys.marketplace.listing("item-01")).toEqual(["marketplace", "listing", "item-01"]);
    expect(queryKeys.messaging.messages("conv-01")).toEqual(["messaging", "messages", "conv-01"]);
    expect(queryKeys.admin.dispute("disp-01")).toEqual(["admin", "dispute", "disp-01"]);
  });
});
