import React, { ButtonHTMLAttributes, forwardRef } from "react";
import { Loader2 } from "lucide-react";
import { cn } from "../../utils/cn";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "amber" | "secondary" | "safelight" | "outline" | "ghost" | "link";
  size?: "sm" | "md" | "lg" | "icon";
  isLoading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      className,
      variant = "primary",
      size = "md",
      isLoading = false,
      disabled,
      leftIcon,
      rightIcon,
      children,
      ...props
    },
    ref
  ) => {
    const baseStyles =
      "inline-flex items-center justify-center font-semibold transition-all duration-150 rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#D97706] focus-visible:ring-offset-2 focus-visible:ring-offset-[#FAF8F5] disabled:opacity-50 disabled:cursor-not-allowed disabled:pointer-events-none active:scale-[0.99] select-none cursor-pointer";

    const variants = {
      primary:
        "bg-[#111111] text-white hover:bg-stone-800 shadow-sm",
      amber:
        "bg-[#D97706] text-white hover:bg-[#B45309] shadow-sm",
      secondary:
        "bg-stone-100 text-stone-800 hover:bg-stone-200 border border-stone-200",
      safelight:
        "bg-safelight-red text-white hover:bg-safelight-deep shadow-sm",
      outline:
        "border border-stone-300 text-stone-700 hover:border-[#D97706] hover:text-[#D97706] hover:bg-[#FFF7ED]/50 bg-white",
      ghost: "text-stone-600 hover:bg-stone-100 hover:text-stone-900",
      link: "text-[#D97706] underline-offset-4 hover:underline p-0 h-auto",
    };

    const sizes = {
      sm: "h-9 px-3.5 text-xs gap-1.5 rounded-lg",
      md: "h-11 px-4 text-sm gap-2",
      lg: "h-12 px-6 text-base gap-2.5",
      icon: "h-10 w-10 p-0",
    };

    return (
      <button
        ref={ref}
        disabled={disabled || isLoading}
        aria-busy={isLoading || undefined}
        className={cn(baseStyles, variants[variant], sizes[size], className)}
        {...props}
      >
        {isLoading ? (
          <Loader2 className="w-4 h-4 animate-spin text-current shrink-0" />
        ) : (
          leftIcon && <span className="inline-flex shrink-0">{leftIcon}</span>
        )}
        {children !== undefined && children !== null && <span>{children}</span>}
        {!isLoading && rightIcon && (
          <span className="inline-flex shrink-0">{rightIcon}</span>
        )}
      </button>
    );
  }
);
Button.displayName = "Button";
