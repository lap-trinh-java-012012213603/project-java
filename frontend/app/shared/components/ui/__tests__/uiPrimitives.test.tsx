import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Badge } from "@/shared/components/ui/Badge";
import { Button } from "@/shared/components/ui/Button";
import { Drawer } from "@/shared/components/ui/Drawer";
import { Input } from "@/shared/components/ui/Input";
import { Modal } from "@/shared/components/ui/Modal";
import { cn } from "@/shared/utils/cn";

describe("FE-C03 Shared UI Primitives & cn utility", () => {
  it("merges conflicting Tailwind classes cleanly via cn()", () => {
    expect(cn("px-2 py-1 bg-white", false && "hidden", "px-4 bg-amber-50")).toBe(
      "py-1 px-4 bg-amber-50"
    );
  });

  it("renders Button variants, loading state and disables clicks while loading", () => {
    const onClick = vi.fn();
    const { rerender } = render(
      <Button variant="primary" onClick={onClick}>
        Đặt Tráng Scan
      </Button>
    );

    const btn = screen.getByRole("button", { name: "Đặt Tráng Scan" });
    expect(btn.className).toContain("bg-[#111111]");
    fireEvent.click(btn);
    expect(onClick).toHaveBeenCalledTimes(1);

    rerender(
      <Button variant="safelight" isLoading onClick={onClick}>
        Đang xử lý
      </Button>
    );
    const loadingBtn = screen.getByRole("button", { name: "Đang xử lý" }) as HTMLButtonElement;
    expect(loadingBtn.disabled).toBe(true);
    expect(loadingBtn.className).toContain("bg-safelight-red");
    fireEvent.click(loadingBtn);
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it("renders Input with associated label, required asterisk and validation error message", () => {
    render(
      <Input
        label="Email đăng nhập"
        required
        placeholder="nhiepanh@analoghub.vn"
        error="Email không đúng định dạng"
      />
    );

    const input = screen.getByLabelText(/Email đăng nhập/i);
    expect(input).toBeTruthy();
    expect(input.getAttribute("aria-invalid")).toBe("true");
    expect(screen.getByText("Email không đúng định dạng")).toBeTruthy();
  });

  it("renders Badge variants for film format and process tags", () => {
    render(
      <div>
        <Badge variant="amber">135mm</Badge>
        <Badge variant="emerald" size="sm">
          C-41
        </Badge>
      </div>
    );

    expect(screen.getByText("135mm").className).toContain("text-kodak-gold");
    expect(screen.getByText("C-41").className).toContain("text-fuji-emerald");
  });

  it("traps Tab / Shift+Tab focus inside Modal and closes on Escape key", () => {
    const onClose = vi.fn();

    render(
      <Modal
        isOpen
        onClose={onClose}
        title="Báo cáo cuộn phim trắng"
        description="Xác nhận hoàn phí scan cho khách hàng"
        footer={
          <>
            <button type="button">Hủy bỏ</button>
            <button type="button">Xác nhận</button>
          </>
        }
      >
        <input aria-label="Ghi chú kỹ thuật" />
      </Modal>
    );

    const dialog = screen.getByRole("dialog");
    expect(dialog.getAttribute("aria-modal")).toBe("true");
    expect(document.body.style.overflow).toBe("hidden");

    const closeBtn = screen.getByLabelText("Đóng hộp thoại");
    const noteInput = screen.getByLabelText("Ghi chú kỹ thuật");
    const cancelBtn = screen.getByRole("button", { name: "Hủy bỏ" });
    const confirmBtn = screen.getByRole("button", { name: "Xác nhận" });

    expect(document.activeElement).toBe(closeBtn);

    confirmBtn.focus();
    expect(document.activeElement).toBe(confirmBtn);
    fireEvent.keyDown(window, { key: "Tab", shiftKey: false });
    expect(document.activeElement).toBe(closeBtn);

    closeBtn.focus();
    fireEvent.keyDown(window, { key: "Tab", shiftKey: true });
    expect(document.activeElement).toBe(confirmBtn);

    expect(noteInput).toBeTruthy();
    expect(cancelBtn).toBeTruthy();

    fireEvent.keyDown(window, { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByTestId("modal-backdrop"));
    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it("preserves focus inside Modal input across parent re-renders with inline onClose", () => {
    const { rerender } = render(
      <Modal isOpen onClose={() => {}} title="Ghi chú kỹ thuật">
        <input aria-label="Ghi chú Push/Pull" value="" onChange={() => {}} />
      </Modal>
    );

    const input = screen.getByLabelText("Ghi chú Push/Pull");
    input.focus();
    expect(document.activeElement).toBe(input);

    rerender(
      <Modal isOpen onClose={() => {}} title="Ghi chú kỹ thuật">
        <input aria-label="Ghi chú Push/Pull" value="Push +1" onChange={() => {}} />
      </Modal>
    );

    expect(document.activeElement).toBe(input);
  });

  it("renders Drawer on specified side and closes on Escape or backdrop click", () => {
    const onClose = vi.fn();

    render(
      <Drawer isOpen onClose={onClose} title="AI Film Doctor" side="right">
        <p>Tư vấn công thức tráng C-41</p>
      </Drawer>
    );

    expect(screen.getByText("AI Film Doctor")).toBeTruthy();
    expect(screen.getByText("Tư vấn công thức tráng C-41")).toBeTruthy();

    fireEvent.keyDown(window, { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByTestId("drawer-backdrop"));
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});
