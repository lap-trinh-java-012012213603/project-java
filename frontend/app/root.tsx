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
    href: "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap",
  },
];

export function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="vi" className="light">
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <Meta />
        <Links />
      </head>
      <body className="min-h-screen bg-[#FAF8F5] text-[#111111] antialiased font-sans selection:bg-amber-100 selection:text-amber-900">
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
        ? "Trang bạn tìm kiếm không tồn tại trên hệ thống AnalogHub."
        : error.statusText || details;
  } else if (import.meta.env.DEV && error && error instanceof Error) {
    details = error.message;
    stack = error.stack;
  }

  return (
    <main className="min-h-screen flex items-center justify-center p-6 bg-[#FAF8F5] text-[#111111]">
      <div className="max-w-xl w-full p-8 bg-white border border-[#E6E4E0] rounded-2xl shadow-lg space-y-4">
        <span className="inline-flex items-center gap-1.5 px-3 py-1 text-xs font-semibold uppercase rounded-full bg-[#FFF1F2] text-[#E11D48] border border-rose-200">
          <span className="w-2 h-2 rounded-full bg-[#E11D48]" />
          Thông báo hệ thống
        </span>
        <h1 className="text-3xl font-extrabold tracking-tight text-[#111111]">{message}</h1>
        <p className="text-sm text-stone-600">{details}</p>
        {stack && (
          <pre className="w-full p-4 overflow-x-auto text-xs font-mono bg-stone-50 border border-stone-200 rounded-xl text-stone-700">
            <code>{stack}</code>
          </pre>
        )}
      </div>
    </main>
  );
}
