import React from "react";
import { Film } from "lucide-react";
import { Link, Outlet, useLocation, useSearchParams } from "react-router";
import { buildAuthLinkWithRedirect } from "@/features/iam/utils/authRedirect";

export const AuthLayout: React.FC = () => {
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const isRegisterPage = location.pathname.includes("/auth/register");

  const loginHref = buildAuthLinkWithRedirect("/auth/login", searchParams);
  const registerHref = buildAuthLinkWithRedirect("/auth/register", searchParams);

  return (
    <div className="min-h-screen bg-[#FAF8F5] text-[#111111] flex flex-col justify-between selection:bg-amber-100 selection:text-amber-900">
      {/* Header Section (64px height, clean light style matching Stitch) */}
      <header className="w-full h-16 bg-white border-b border-[#E6E4E0] px-6 md:px-8 flex items-center justify-between sticky top-0 z-50 shadow-2xs">
        <div className="flex items-center gap-3">
          <Link to="/" className="flex items-center gap-2 group">
            <span className="w-2.5 h-2.5 rounded-full bg-[#E11D48] inline-block shadow-xs group-hover:scale-110 transition-transform" />
            <span className="font-extrabold tracking-tight text-xl text-[#111111]">
              ANALOG<span className="text-[#D97706]">HUB</span>
            </span>
          </Link>
          <span className="hidden sm:inline-flex items-center gap-1 bg-[#FFF7ED] border border-[#FED7AA] text-[#D97706] font-semibold text-xs px-2.5 py-0.5 rounded-full ml-1">
            <Film className="w-3.5 h-3.5" />
            <span>FILM COMMUNITY</span>
          </span>
        </div>

        <div className="flex items-center gap-6">
          <nav className="hidden md:flex items-center gap-6 text-sm font-medium text-stone-700">
            <Link to="/" className="hover:text-[#D97706] transition-colors">
              Khám phá Film Lab
            </Link>
            <Link to="/" className="hover:text-[#D97706] transition-colors">
              Chợ máy ảnh
            </Link>
          </nav>

          {isRegisterPage ? (
            <div className="flex items-center gap-3">
              <span className="text-sm text-stone-500 hidden sm:inline">Đã có tài khoản?</span>
              <Link
                to={loginHref}
                className="px-4 py-2 text-sm font-semibold border border-[#D97706]/50 rounded-lg text-[#111111] hover:border-[#D97706] hover:bg-[#FFF7ED]/60 transition-all shadow-2xs"
              >
                Đăng nhập
              </Link>
            </div>
          ) : (
            <Link
              to={registerHref}
              className="px-4 py-2 text-sm font-semibold border border-[#D97706] rounded-lg text-[#D97706] hover:bg-amber-50 transition-all shadow-2xs"
            >
              Đăng ký tài khoản
            </Link>
          )}
        </div>
      </header>

      {/* Main Content Canvas */}
      <main className="w-full flex-1 flex items-center justify-center py-8 md:py-12 px-4 sm:px-6">
        <Outlet />
      </main>

      {/* Minimalist Clean Light Footer */}
      <footer className="w-full border-t border-[#E6E4E0] bg-white py-6 px-6 sm:px-8 mt-auto">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-stone-500">
          <p>© 2025 AnalogHub. All rights reserved. Nền tảng nhiếp ảnh phim chuyên nghiệp.</p>
          <div className="flex items-center gap-4 flex-wrap justify-center">
            <Link to="/" className="hover:text-[#111111] transition-colors">
              Chính sách bảo mật
            </Link>
            <span>•</span>
            <Link to="/" className="hover:text-[#111111] transition-colors">
              Điều khoản dịch vụ
            </Link>
            <span>•</span>
            <Link to="/" className="hover:text-[#111111] transition-colors">
              Film Lab đối tác
            </Link>
            <span>•</span>
            <Link to="/" className="hover:text-[#111111] transition-colors">
              Trung tâm hỗ trợ
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
};

export default AuthLayout;
