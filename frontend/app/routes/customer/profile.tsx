import { FlaskConical, MessageSquare, ShieldCheck, Store, Wallet } from "lucide-react";
import { Link } from "react-router";
import { AddressBook } from "@/features/iam/components/AddressBook";
import { ChangePasswordCard } from "@/features/iam/components/ChangePasswordCard";
import { UserProfile } from "@/features/iam/components/UserProfile";
import { useAuthStore } from "@/shared/stores/useAuthStore";

export function meta() {
  return [{ title: "Hồ Sơ Cá Nhân, Sổ Địa Chỉ & Bảo Mật — AnalogHub" }];
}

export default function CustomerProfilePage() {
  const { user } = useAuthStore();

  return (
    <div className="min-h-screen bg-[#FAF8F5] text-[#111111] flex flex-col justify-between">
      {/* 1. HEADER CHÍNH (Clean White Topbar matching Stitch) */}
      <header className="sticky top-0 z-50 bg-white border-b border-[#E6E4E0] shadow-2xs">
        <div className="max-w-[1440px] mx-auto px-4 sm:px-8 h-16 flex items-center justify-between">
          <div className="flex items-center gap-8">
            <Link to="/" className="flex items-center gap-2 group">
              <span className="w-3 h-3 rounded-full bg-[#E11D48] shadow-2xs group-hover:scale-110 transition-transform" />
              <span className="text-xl font-extrabold tracking-tight text-[#111111]">
                ANALOG<span className="text-[#D97706]">HUB</span>
              </span>
              <span className="hidden sm:inline-block ml-1 px-2 py-0.5 text-[11px] font-bold uppercase tracking-wider bg-[#FFF7ED] text-[#B45309] border border-[#FED7AA] rounded-md">
                Film Community
              </span>
            </Link>

            <nav className="hidden md:flex items-center gap-6 text-sm font-semibold text-stone-600">
              <Link to="/" className="hover:text-[#111111] transition-colors flex items-center gap-1.5">
                <FlaskConical className="w-4 h-4 text-[#D97706]" />
                <span>Khám phá Film Lab</span>
              </Link>
              <Link to="/" className="hover:text-[#111111] transition-colors flex items-center gap-1.5">
                <Store className="w-4 h-4 text-stone-500" />
                <span>Chợ máy ảnh C2C</span>
              </Link>
              <Link to="/" className="hover:text-[#111111] transition-colors flex items-center gap-1.5">
                <MessageSquare className="w-4 h-4 text-stone-500" />
                <span>Tin nhắn</span>
                <span className="w-2 h-2 rounded-full bg-[#E11D48]" />
              </Link>
              <Link to="/" className="hover:text-[#111111] transition-colors flex items-center gap-1.5">
                <Wallet className="w-4 h-4 text-stone-500" />
                <span>Ví của tôi</span>
              </Link>
            </nav>
          </div>

          <div className="flex items-center gap-4">
            <span className="hidden sm:inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#ECFDF5] border border-[#A7F3D0] text-[#047857] text-xs font-bold">
              <ShieldCheck className="w-3.5 h-3.5" />
              <span>IAM Bảo Mật Phiên JWT</span>
            </span>

            {user && (
              <div className="hidden lg:flex flex-col text-right">
                <span className="text-xs font-bold text-[#111111] leading-none">
                  {user.fullName}
                </span>
                <span className="text-[11px] text-stone-500 font-mono">{user.email}</span>
              </div>
            )}
          </div>
        </div>
      </header>

      {/* 2. MAIN CONTENT (2-Column Grid 7 / 5 matching Stitch) */}
      <main className="max-w-[1440px] w-full mx-auto px-4 sm:px-8 py-8 flex-1">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-7 items-start">
          {/* CỘT TRÁI (7 CỘT): Tổng quan Hồ sơ, Gu chụp phim & Sổ địa chỉ */}
          <div className="lg:col-span-7 space-y-7">
            <UserProfile />
            <AddressBook />
          </div>

          {/* CỘT PHẢI (5 CỘT): Đổi mật khẩu & Bảo mật phiên */}
          <div className="lg:col-span-5">
            <ChangePasswordCard />
          </div>
        </div>
      </main>

      {/* 3. FOOTER ĐỒNG BỘ HỆ THỐNG */}
      <footer className="bg-white border-t border-[#E6E4E0] mt-12 py-6">
        <div className="max-w-[1440px] mx-auto px-4 sm:px-8 flex flex-col md:flex-row items-center justify-between gap-4 text-xs text-stone-500">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-[#E11D48]" />
            <span className="font-bold text-[#111111]">AnalogHub</span>
            <span>
              — © 2025 AnalogHub. All rights reserved. Nền tảng nhiếp ảnh phim chuyên nghiệp.
            </span>
          </div>
          <div className="flex flex-wrap items-center gap-6 font-medium">
            <Link to="/" className="hover:text-[#111111] transition-colors">
              Chính sách bảo mật
            </Link>
            <Link to="/" className="hover:text-[#111111] transition-colors">
              Điều khoản dịch vụ
            </Link>
            <Link to="/" className="hover:text-[#111111] transition-colors">
              Film Lab đối tác
            </Link>
            <Link to="/" className="hover:text-[#111111] transition-colors">
              Trung tâm hỗ trợ
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
