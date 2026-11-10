import { useState } from "react";
import {
  ArrowRight,
  Camera,
  Film,
  FlaskConical,
  LogIn,
  LogOut,
  Search,
  ShieldCheck,
  Sparkles,
  User,
  UserPlus,
} from "lucide-react";
import { Link } from "react-router";
import type { Route } from "./+types/home";
import { Badge } from "@/shared/components/ui/Badge";
import { Button } from "@/shared/components/ui/Button";
import { Input } from "@/shared/components/ui/Input";
import { useAuthStore } from "@/shared/stores/useAuthStore";

export function meta({}: Route.MetaArgs) {
  return [
    { title: "AnalogHub — Hệ sinh thái Nhiếp ảnh Phim" },
    {
      name: "description",
      content:
        "Nền tảng đặt lịch tráng scan phòng Lab, kho lưu trữ số, chợ thiết bị phim Escrow 72h và trợ lý AI nhiếp ảnh phim.",
    },
  ];
}

export default function Home() {
  const [searchQuery, setSearchQuery] = useState("");
  const { isAuthenticated, user, logout } = useAuthStore();

  return (
    <div className="min-h-screen bg-[#FAF8F5] text-[#111111] flex flex-col justify-between">
      {/* Clean Light Mode Header */}
      <header className="w-full h-16 bg-white border-b border-[#E6E4E0] sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-full flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link to="/" className="flex items-center gap-2 group">
              <span className="w-2.5 h-2.5 rounded-full bg-[#E11D48] shadow-xs group-hover:scale-110 transition-transform" />
              <span className="font-extrabold tracking-tight text-xl text-[#111111]">
                ANALOG<span className="text-[#D97706]">HUB</span>
              </span>
            </Link>
            <Badge variant="amber" size="sm" className="hidden sm:inline-flex gap-1 rounded-full">
              <Film className="w-3 h-3" />
              <span>FILM COMMUNITY</span>
            </Badge>
          </div>

          {isAuthenticated && user ? (
            <div className="flex items-center gap-3">
              <span className="text-xs text-stone-600 hidden sm:inline">
                Xin chào, <strong className="text-[#111111]">{user.fullName}</strong>
              </span>
              <Link to="/customer/profile">
                <Button variant="outline" size="sm" leftIcon={<User className="w-3.5 h-3.5" />}>
                  Hồ sơ cá nhân
                </Button>
              </Link>
              <Button
                variant="secondary"
                size="sm"
                leftIcon={<LogOut className="w-3.5 h-3.5" />}
                onClick={() => logout()}
              >
                Đăng xuất
              </Button>
            </div>
          ) : (
            <div className="flex items-center gap-2.5">
              <Link to="/auth/login">
                <Button variant="ghost" size="sm" leftIcon={<LogIn className="w-3.5 h-3.5" />}>
                  Đăng nhập
                </Button>
              </Link>
              <Link to="/auth/register">
                <Button variant="primary" size="sm" leftIcon={<UserPlus className="w-3.5 h-3.5" />}>
                  Đăng ký tài khoản
                </Button>
              </Link>
            </div>
          )}
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-12 flex-1 space-y-10">
        <section className="p-8 sm:p-10 bg-white border border-[#E6E4E0] rounded-2xl shadow-sm space-y-6">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="amber">135mm / 120mm</Badge>
            <Badge variant="emerald">C-41 &amp; ECN-2</Badge>
            <Badge variant="indigo">TIFF / JPG 48-bit</Badge>
            <Badge variant="outline">Escrow 72h</Badge>
          </div>

          <div className="space-y-3 max-w-3xl">
            <h1 className="text-3xl sm:text-4xl font-extrabold tracking-tight text-[#111111]">
              Hệ sinh thái Nhiếp ảnh Phim{" "}
              <span className="text-[#D97706]">AnalogHub</span>
            </h1>
            <p className="text-sm sm:text-base text-stone-600 leading-relaxed">
              Khám phá phòng Lab uy tín, theo dõi tiến độ tráng scan cuộn phim thời gian thực,
              lưu trữ kho âm bản số hóa và giao dịch máy ảnh cơ qua ký quỹ Escrow an toàn.
            </p>
          </div>

          <div className="max-w-lg">
            <Input
              label="Tìm nhanh Phòng Lab hoặc Dòng Phim"
              placeholder="Ví dụ: Kodak Portra 400, Lab Sài Gòn..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              leftIcon={<Search className="w-4 h-4" />}
              helperText="Hỗ trợ lọc theo khổ phim 135, 120 và quy trình hóa chất C-41, ECN-2, B&W."
            />
          </div>

          <div className="flex flex-wrap items-center gap-3 pt-2">
            <Link to={isAuthenticated ? "/customer/profile" : "/auth/login"}>
              <Button
                variant="primary"
                leftIcon={<FlaskConical className="w-4 h-4 text-[#F59E0B]" />}
                rightIcon={<ArrowRight className="w-4 h-4" />}
              >
                {isAuthenticated ? "Quản lý Hồ sơ & Sổ địa chỉ" : "Bắt đầu trải nghiệm"}
              </Button>
            </Link>
            <Link to="/customer/profile">
              <Button variant="outline" leftIcon={<ShieldCheck className="w-4 h-4 text-[#059669]" />}>
                Hồ sơ &amp; Bảo mật IAM
              </Button>
            </Link>
          </div>

          {/* 3 Feature Highlight Cards */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-4 border-t border-stone-100">
            <div className="p-4 rounded-xl bg-[#FFFDF9] border border-[#FED7AA] space-y-1.5">
              <div className="w-9 h-9 rounded-lg bg-[#FFF7ED] text-[#D97706] flex items-center justify-center">
                <FlaskConical className="w-4 h-4" />
              </div>
              <h3 className="text-sm font-bold text-[#111111]">Đặt Tráng &amp; Scan Film Lab</h3>
              <p className="text-xs text-stone-600">
                Theo dõi trực tuyến từng bước C-41, ECN-2, B&amp;W từ nhận cuộn phim tới trả ảnh số.
              </p>
            </div>

            <div className="p-4 rounded-xl bg-[#ECFDF5]/40 border border-[#A7F3D0] space-y-1.5">
              <div className="w-9 h-9 rounded-lg bg-[#ECFDF5] text-[#059669] flex items-center justify-center">
                <Camera className="w-4 h-4" />
              </div>
              <h3 className="text-sm font-bold text-[#111111]">Chợ Máy Ảnh Ký Quỹ Escrow</h3>
              <p className="text-xs text-stone-600">
                Bảo vệ người mua 72h kiểm tra cơ tốc, buồng phim và thấu kính trước khi giải ngân.
              </p>
            </div>

            <div className="p-4 rounded-xl bg-[#EEF2FF]/40 border border-[#C7D2FE] space-y-1.5">
              <div className="w-9 h-9 rounded-lg bg-[#EEF2FF] text-[#4F46E5] flex items-center justify-center">
                <Sparkles className="w-4 h-4" />
              </div>
              <h3 className="text-sm font-bold text-[#111111]">Kho Âm Bản Số &amp; AI</h3>
              <p className="text-xs text-stone-600">
                Lưu trữ cuộn phim độ phân giải gốc và gợi ý dòng phim theo gu chụp cá nhân.
              </p>
            </div>
          </div>
        </section>
      </main>

      {/* Clean Light Footer */}
      <footer className="w-full border-t border-[#E6E4E0] bg-white py-6 px-4 sm:px-8">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-stone-500">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-[#E11D48]" />
            <span className="font-bold text-[#111111]">AnalogHub</span>
            <span>— © 2025 AnalogHub. Nền tảng nhiếp ảnh phim chuyên nghiệp.</span>
          </div>
          <div className="flex items-center gap-4">
            <span>Chính sách bảo mật</span>
            <span>•</span>
            <span>Điều khoản dịch vụ</span>
            <span>•</span>
            <span>Film Lab đối tác</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
