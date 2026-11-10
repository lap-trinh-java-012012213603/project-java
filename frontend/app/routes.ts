import { type RouteConfig, index, layout, prefix, route } from "@react-router/dev/routes";

export default [
  index("routes/home.tsx"),
  route("customer/profile", "routes/customer/profile.tsx"),
  layout("layouts/AuthLayout.tsx", [
    ...prefix("auth", [
      route("login", "routes/auth/login.tsx"),
      route("register", "routes/auth/register.tsx"),
      route("oauth2/callback", "routes/auth/oauth2-callback.tsx"),
    ]),
  ]),
] satisfies RouteConfig;
