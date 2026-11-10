import React, { useEffect, useId, useRef } from "react";
import { X } from "lucide-react";
import { cn } from "../../utils/cn";

const FOCUSABLE_SELECTOR = [
  "a[href]",
  "button:not([disabled])",
  "textarea:not([disabled])",
  "input:not([disabled])",
  "select:not([disabled])",
  '[tabindex]:not([tabindex="-1"])',
].join(", ");

export interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  description?: string;
  maxWidth?: "sm" | "md" | "lg" | "xl" | "2xl" | "full";
  children: React.ReactNode;
  footer?: React.ReactNode;
}

export const Modal: React.FC<ModalProps> = ({
  isOpen,
  onClose,
  title,
  description,
  maxWidth = "md",
  children,
  footer,
}) => {
  const modalRef = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  const titleId = useId();
  const descriptionId = useId();

  useEffect(() => {
    if (!isOpen) return;

    const previousFocused = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    const getFocusableElements = (): HTMLElement[] => {
      if (!modalRef.current) return [];
      return Array.from(modalRef.current.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
    };

    const initialFocusables = getFocusableElements();
    if (initialFocusables.length > 0) {
      initialFocusables[0].focus();
    } else {
      modalRef.current?.focus();
    }

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onCloseRef.current();
        return;
      }

      if (e.key === "Tab") {
        const focusables = getFocusableElements();
        if (focusables.length === 0) {
          e.preventDefault();
          modalRef.current?.focus();
          return;
        }

        const firstElement = focusables[0];
        const lastElement = focusables[focusables.length - 1];
        const activeElement = document.activeElement as HTMLElement | null;
        const isFocusInsideModal =
          activeElement !== null && modalRef.current?.contains(activeElement);

        if (e.shiftKey) {
          if (!isFocusInsideModal || activeElement === firstElement) {
            e.preventDefault();
            lastElement.focus();
          }
        } else if (!isFocusInsideModal || activeElement === lastElement) {
          e.preventDefault();
          firstElement.focus();
        }
      }
    };

    window.addEventListener("keydown", handleKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow || "unset";
      window.removeEventListener("keydown", handleKeyDown);
      if (previousFocused && typeof previousFocused.focus === "function") {
        previousFocused.focus();
      }
    };
  }, [isOpen]);

  if (!isOpen) return null;

  const maxWidthClasses = {
    sm: "max-w-sm",
    md: "max-w-md",
    lg: "max-w-lg",
    xl: "max-w-xl",
    "2xl": "max-w-2xl",
    full: "max-w-4xl",
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop mờ */}
      <div
        data-testid="modal-backdrop"
        className="fixed inset-0 bg-black/80 backdrop-blur-sm transition-opacity"
        onClick={onClose}
      />

      {/* Hộp Modal */}
      <div
        ref={modalRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        aria-describedby={description ? descriptionId : undefined}
        tabIndex={-1}
        className={cn(
          "relative w-full bg-darkroom-900 border border-darkroom-750 rounded-[var(--radius-modal)] shadow-[var(--shadow-darkroom-lg)] overflow-hidden z-10 flex flex-col max-h-[90vh] focus:outline-none",
          maxWidthClasses[maxWidth]
        )}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-darkroom-800">
          <div>
            {title && (
              <h3 id={titleId} className="text-base font-semibold text-gray-100">
                {title}
              </h3>
            )}
            {description && (
              <p id={descriptionId} className="text-xs text-gray-400 mt-0.5">
                {description}
              </p>
            )}
          </div>
          <button
            type="button"
            aria-label="Đóng hộp thoại"
            onClick={onClose}
            className="text-gray-400 hover:text-white p-1 rounded-md hover:bg-darkroom-800 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-kodak-amber"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body (Có scroll riêng nếu nội dung dài) */}
        <div className="p-6 overflow-y-auto flex-1">{children}</div>

        {/* Footer */}
        {footer && (
          <div className="flex items-center justify-end gap-3 px-6 py-3.5 bg-darkroom-950 border-t border-darkroom-800">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
};
