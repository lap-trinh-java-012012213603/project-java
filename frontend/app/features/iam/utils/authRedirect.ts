interface RedirectStateShape {
  from?: string | { pathname?: string; search?: string };
}

export function isSafeInternalRedirect(candidate: string | null | undefined): candidate is string {
  if (!candidate) return false;
  const trimmed = candidate.trim();
  if (!trimmed.startsWith("/")) return false;
  if (trimmed.startsWith("//") || trimmed.startsWith("/\\")) return false;
  if (trimmed.startsWith("/auth/")) return false;
  return true;
}

export function resolvePostAuthRedirect(
  searchParams: URLSearchParams,
  locationState: unknown,
  fallbackPath = "/"
): string {
  const queryRedirect = searchParams.get("redirect") || searchParams.get("state");
  if (isSafeInternalRedirect(queryRedirect)) {
    return queryRedirect.trim();
  }

  if (locationState && typeof locationState === "object" && "from" in locationState) {
    const rawFrom = (locationState as RedirectStateShape).from;
    if (typeof rawFrom === "string" && isSafeInternalRedirect(rawFrom)) {
      return rawFrom.trim();
    }
    if (rawFrom && typeof rawFrom === "object" && typeof rawFrom.pathname === "string") {
      const combined = `${rawFrom.pathname}${rawFrom.search || ""}`;
      if (isSafeInternalRedirect(combined)) {
        return combined;
      }
    }
  }

  return fallbackPath;
}

export function buildAuthLinkWithRedirect(
  targetAuthPath: "/auth/login" | "/auth/register",
  searchParams: URLSearchParams
): string {
  const redirectParam = searchParams.get("redirect");
  if (isSafeInternalRedirect(redirectParam)) {
    return `${targetAuthPath}?redirect=${encodeURIComponent(redirectParam)}`;
  }
  return targetAuthPath;
}

export function getBrowserDeviceInfo(): string | undefined {
  if (typeof navigator === "undefined" || !navigator.userAgent) {
    return undefined;
  }
  return navigator.userAgent.slice(0, 255);
}
