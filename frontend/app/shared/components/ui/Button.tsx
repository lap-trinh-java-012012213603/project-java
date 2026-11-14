import React, { ButtonHTMLAttributes, forwardRef } from "react";
import { Loader2 } from "lucide-react";
import { cn } from "../../utils/cn";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "safelight" | "outline" | "ghost" | "link";
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
      "inline-flex items-center justify-center font-medium transition-all duration-150 rounded-[var(--radius-film)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-kodak-amber focus-visible:ring-offset-2 focus-visible:ring-offset-darkroom-950 disabled:opacity-50 disabled:cursor-not-allowed disabled:pointer-events-none active:scale-[0.98] select-none";

    const variants = {
      primary:
        "bg-kodak-amber text-darkroom-950 font-semibold hover:bg-kodak-gold shadow-[var(--shadow-amber-glow)]",
      secondary:
        "bg-darkroom-800 text-gray-100 hover:bg-darkroom-700 border border-darkroom-600",
      safelight:
        "bg-safelight-red text-white hover:bg-safelight-deep shadow-[var(--shadow-red-glow)]",
      outline:
        "border border-darkroom-600 text-gray-200 hover:border-kodak-amber hover:text-kodak-amber bg-transparent",
      ghost: "text-gray-300 hover:bg-darkroom-800 hover:text-white",
      link: "text-kodak-amber underline-offset-4 hover:underline p-0 h-auto",
    };

    const sizes = {
      sm: "h-8 px-3 text-xs gap-1.5",
      md: "h-10 px-4 text-sm gap-2",
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
