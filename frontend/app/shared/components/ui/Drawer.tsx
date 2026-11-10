import React, { useEffect, useId, useRef } from "react";
import { X } from "lucide-react";
import { cn } from "../../utils/cn";

export interface DrawerProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  side?: "right" | "left" | "bottom";
  children: React.ReactNode;
  footer?: React.ReactNode;
  className?: string;
}

export const Drawer: React.FC<DrawerProps> = ({
  isOpen,
  onClose,
  title,
  side = "right",
  children,
  footer,
  className,
}) => {
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;
  const titleId = useId();

  useEffect(() => {
    if (!isOpen) return;

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onCloseRef.current();
      }
    };

    window.addEventListener("keydown", handleKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow || "unset";
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen]);

  if (!isOpen) return null;

  const sideClasses = {
    right: "inset-y-0 right-0 w-full sm:w-[480px] border-l border-[#E6E4E0]",
    left: "inset-y-0 left-0 w-full sm:w-[480px] border-r border-[#E6E4E0]",
    bottom:
      "inset-x-0 bottom-0 h-[80vh] rounded-t-2xl border-t border-[#E6E4E0]",
  };

  return (
    <div className="fixed inset-0 z-50">
      <div
        data-testid="drawer-backdrop"
        className="fixed inset-0 bg-stone-900/40 backdrop-blur-xs transition-opacity"
        onClick={onClose}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        className={cn(
          "fixed bg-white shadow-2xl flex flex-col z-10 text-[#111111]",
          sideClasses[side],
          className
        )}
      >
        <div className="flex items-center justify-between px-6 py-4 border-b border-stone-200">
          <h3 id={titleId} className="text-base font-bold text-[#111111]">
            {title}
          </h3>
          <button
            type="button"
            aria-label="Đóng ngăn trượt"
            onClick={onClose}
            className="text-stone-400 hover:text-stone-900 p-1.5 rounded-lg hover:bg-stone-100 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#D97706] cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>
        <div className="p-6 overflow-y-auto flex-1">{children}</div>
        {footer && (
          <div className="flex items-center justify-end gap-3 px-6 py-3.5 bg-[#FAF8F5] border-t border-stone-200">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
};
