import { useState } from "react";
import { Bot, Camera, Film, LogIn, LogOut, Search, Sparkles, UserPlus } from "lucide-react";
import { Link } from "react-router";
import type { Route } from "./+types/home";
import { Badge } from "@/shared/components/ui/Badge";
import { Button } from "@/shared/components/ui/Button";
import { Drawer } from "@/shared/components/ui/Drawer";
import { Input } from "@/shared/components/ui/Input";
import { Modal } from "@/shared/components/ui/Modal";
import { useAuthStore } from "@/shared/stores/useAuthStore";

export function meta({}: Route.MetaArgs) {
  return [
    { title: "AnalogHub — Hệ sinh thái Nhiếp ảnh Phim" },
    {
      name: "description",
      content:
        "Nền tảng đặt lịch tráng scan phòng Lab, kho lưu trữ số, chợ thiết bị phim Escrow 72h và trợ lý AI buồng tối.",
    },
  ];
}

export default function Home() {
  const [searchQuery, setSearchQuery] = useState("");
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const { isAuthenticated, user, logout } = useAuthStore();

  return (
    <main className="min-h-screen bg-darkroom-950 text-gray-100 film-grain-bg">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12 space-y-10">
        {/* Top Auth Bar */}
        <header className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-[var(--radius-film)] bg-kodak-amber text-darkroom-950 font-black flex items-center justify-center text-sm shadow-[var(--shadow-amber-glow)]">
              AH
            </div>
            <span className="font-bold tracking-tight text-base">
              Analog<span className="text-kodak-amber">Hub</span>
            </span>
          </div>

          {isAuthenticated && user ? (
            <div className="flex items-center gap-3">
              <span className="text-xs text-gray-300">
                Xin chào, <strong className="text-kodak-amber">{user.fullName}</strong>
              </span>
              <Button
                variant="outline"
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
                  Đăng ký
                </Button>
              </Link>
            </div>
          )}
        </header>

        {/* Hero Banner */}
        <section className="p-8 bg-darkroom-900 border border-darkroom-800 rounded-[var(--radius-card)] shadow-[var(--shadow-darkroom-lg)] film-sprocket-top space-y-6">
          <div className="flex flex-wrap items-center gap-2 pt-2">
            <Badge variant="amber">135mm / 120mm</Badge>
            <Badge variant="emerald">C-41 &amp; ECN-2</Badge>
            <Badge variant="red" size="sm">
              SAFELIGHT DARKROOM
            </Badge>
            <Badge variant="outline">Escrow 72h</Badge>
          </div>

          <div className="space-y-3">
            <h1 className="text-3xl sm:text-4xl font-bold tracking-tight text-gray-100">
              Analog<span className="text-kodak-amber">Hub</span> Darkroom Ecosystem
            </h1>
            <p className="text-sm sm:text-base text-gray-400 max-w-2xl">
              Hệ sinh thái nhiếp ảnh phim khép kín: khám phá phòng Lab uy tín, theo dõi tiến độ
              tráng scan thời gian thực, lưu trữ cuộn phim số hóa và giao dịch máy ảnh an toàn.
            </p>
          </div>

          <div className="max-w-md">
            <Input
              label="Tìm nhanh Phòng Lab hoặc Dòng Phim"
              placeholder="Ví dụ: Kodak Portra 400, Lab Hà Nội..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              leftIcon={<Search className="w-4 h-4" />}
              helperText="Hỗ trợ lọc theo khổ phim 135, 120 và quy trình hóa chất C-41, ECN-2, B&W."
            />
          </div>

          <div className="flex flex-wrap items-center gap-3 pt-2">
            <Button
              variant="primary"
              leftIcon={<Film className="w-4 h-4" />}
              onClick={() => setIsModalOpen(true)}
            >
              Đặt Tráng Scan Ngay
            </Button>
            <Button
              variant="secondary"
              leftIcon={<Camera className="w-4 h-4" />}
              onClick={() => setIsDrawerOpen(true)}
            >
              Mở AI Film Doctor
            </Button>
            <Button variant="outline">Khám Phá Chợ Máy Ảnh</Button>
            <Button variant="safelight" size="sm">
              Cảnh Báo SLA
            </Button>
          </div>
        </section>
      </div>

      {/* Modal Demo (Focus Trap + Esc) */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title="Khởi Tạo Đơn Đặt Tráng Scan"
        description="Kiểm tra bẫy tiêu điểm (Tab / Shift+Tab) và phím tắt Esc để đóng hộp thoại."
        footer={
          <>
            <Button variant="secondary" onClick={() => setIsModalOpen(false)}>
              Đóng
            </Button>
            <Button variant="primary" onClick={() => setIsModalOpen(false)}>
              Xác Nhận Cấu Hình
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <Input
            label="Ghi chú kỹ thuật buồng tối"
            placeholder="Ví dụ: Push +1 stop, giữ lại vỏ canister..."
          />
          <div className="flex items-center gap-2">
            <Badge variant="amber" size="sm">
              KODAK GOLD 200
            </Badge>
            <Badge variant="emerald" size="sm">
              HIGH TIFF 16-BIT
            </Badge>
          </div>
        </div>
      </Modal>

      {/* Drawer Demo */}
      <Drawer
        isOpen={isDrawerOpen}
        onClose={() => setIsDrawerOpen(false)}
        title="AI Film Doctor — Trợ Lý Buồng Tối"
        side="right"
      >
        <div className="space-y-4 text-sm text-gray-300">
          <div className="p-4 rounded-[var(--radius-card)] bg-darkroom-950 border border-darkroom-800 flex items-start gap-3">
            <Bot className="w-5 h-5 text-kodak-amber shrink-0 mt-0.5" />
            <div className="space-y-1">
              <p className="font-medium text-gray-100 flex items-center gap-1.5">
                Chẩn đoán quy trình C-41 / ECN-2
                <Sparkles className="w-3.5 h-3.5 text-kodak-amber" />
              </p>
              <p className="text-xs text-gray-400">
                Sẵn sàng tư vấn nhiệt độ thuốc tráng 38°C, thời gian bù Push/Pull và kiểm tra hạt
                bụi trên ảnh scan.
              </p>
            </div>
          </div>
        </div>
      </Drawer>
    </main>
  );
}
