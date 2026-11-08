import React from "react";
import { ArrowLeft } from "lucide-react";
import { Link, Outlet } from "react-router";

export const AuthLayout: React.FC = () => {
  return (
    <div className="min-h-screen bg-darkroom-950 text-gray-100 film-grain-bg flex flex-col justify-between p-4 sm:p-6">
      {/* Minimalist Top Bar */}
      <header className="max-w-5xl w-full mx-auto flex items-center justify-between py-2">
        <Link to="/" className="inline-flex items-center gap-2.5 group">
          <div className="w-9 h-9 rounded-[var(--radius-film)] bg-kodak-amber text-darkroom-950 flex items-center justify-center font-black text-base shadow-[var(--shadow-amber-glow)] group-hover:scale-105 transition-transform">
            AH
          </div>
          <div className="flex flex-col">
            <span className="font-bold tracking-tight text-base text-gray-100 leading-none">
              Analog<span className="text-kodak-amber">Hub</span>
            </span>
            <span className="text-[10px] text-gray-500 font-mono tracking-widest uppercase">
              Darkroom Auth
            </span>
          </div>
        </Link>

        <Link
          to="/"
          className="inline-flex items-center gap-1.5 text-xs font-medium text-gray-400 hover:text-kodak-amber transition-colors"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>Về trang chủ</span>
        </Link>
      </header>

      {/* Main Centered Auth Card */}
      <main className="w-full flex-1 flex items-center justify-center py-8">
        <div className="w-full max-w-md bg-darkroom-900 border border-darkroom-800 rounded-[var(--radius-card)] shadow-[var(--shadow-darkroom-lg)] film-sprocket-top p-6 sm:p-8">
          <div className="pt-2">
            <Outlet />
          </div>
        </div>
      </main>

      {/* Minimalist Footer */}
      <footer className="text-center py-2 text-[11px] font-mono text-gray-500">
        © 2026 AnalogHub Vietnam • Bảo mật phiên JWT &amp; Refresh Token Rotation
      </footer>
    </div>
  );
};

export default AuthLayout;
