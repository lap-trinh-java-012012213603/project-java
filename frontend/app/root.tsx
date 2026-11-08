import {
  isRouteErrorResponse,
  Links,
  Meta,
  Outlet,
  Scripts,
  ScrollRestoration,
} from "react-router";

import type { Route } from "./+types/root";
import { RootLayout } from "@/layouts/RootLayout";
import "./app.css";

export { RootLayout };

export const links: Route.LinksFunction = () => [
  { rel: "preconnect", href: "https://fonts.googleapis.com" },
  {
    rel: "preconnect",
    href: "https://fonts.gstatic.com",
    crossOrigin: "anonymous",
  },
  {
    rel: "stylesheet",
    href: "https://fonts.googleapis.com/css2?family=Inter:ital,opsz,wght@0,14..32,100..900;1,14..32,100..900&family=JetBrains+Mono:ital,wght@0,400..800;1,400..800&display=swap",
  },
];

export function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="vi" className="dark">
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <Meta />
        <Links />
      </head>
      <body className="min-h-screen bg-darkroom-950 text-gray-100 antialiased font-sans selection:bg-kodak-amber selection:text-darkroom-950">
        <RootLayout>{children}</RootLayout>
        <ScrollRestoration />
        <Scripts />
      </body>
    </html>
  );
}

export default function App() {
  return <Outlet />;
}

export function ErrorBoundary({ error }: Route.ErrorBoundaryProps) {
  let message = "Đã xảy ra lỗi!";
  let details = "Hệ thống gặp sự cố không mong muốn.";
  let stack: string | undefined;

  if (isRouteErrorResponse(error)) {
    message = error.status === 404 ? "404" : `Lỗi ${error.status}`;
    details =
      error.status === 404
        ? "Trang bạn tìm kiếm không tồn tại trong buồng tối AnalogHub."
        : error.statusText || details;
  } else if (import.meta.env.DEV && error && error instanceof Error) {
    details = error.message;
    stack = error.stack;
  }

  return (
    <main className="min-h-screen flex items-center justify-center p-6 bg-darkroom-950 text-gray-100 film-grain-bg">
      <div className="max-w-xl w-full p-6 bg-darkroom-900 border border-darkroom-800 rounded-[var(--radius-card)] shadow-[var(--shadow-darkroom-lg)] space-y-4">
        <span className="inline-block px-2.5 py-0.5 text-xs font-mono uppercase rounded-[var(--radius-film)] bg-safelight-subtle text-safelight-red border border-safelight-red/30">
          Darkroom Alert
        </span>
        <h1 className="text-3xl font-bold font-mono text-kodak-amber">{message}</h1>
        <p className="text-sm text-gray-300">{details}</p>
        {stack && (
          <pre className="w-full p-4 overflow-x-auto text-xs font-mono bg-darkroom-950 border border-darkroom-800 rounded-[var(--radius-film)] text-gray-400">
            <code>{stack}</code>
          </pre>
        )}
      </div>
    </main>
  );
}
