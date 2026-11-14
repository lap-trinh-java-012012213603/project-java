import React from "react";
import { cn } from "../../utils/cn";

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: "amber" | "emerald" | "red" | "neutral" | "outline";
  size?: "sm" | "md";
}

export const Badge: React.FC<BadgeProps> = ({
  className,
  variant = "neutral",
  size = "md",
  children,
  ...props
}) => {
  const variants = {
    amber: "bg-kodak-subtle text-kodak-amber border border-kodak-amber/30",
    emerald: "bg-fuji-subtle text-fuji-emerald border border-fuji-emerald/30",
    red: "bg-safelight-subtle text-safelight-red border border-safelight-red/30",
    neutral: "bg-darkroom-800 text-gray-300 border border-darkroom-700",
    outline: "bg-transparent text-gray-400 border border-darkroom-600",
  };

  const sizes = {
    sm: "text-[10px] px-1.5 py-0.5 tracking-wider font-mono",
    md: "text-xs px-2.5 py-0.5 font-medium",
  };

  return (
    <span
      className={cn(
        "inline-flex items-center justify-center rounded-[var(--radius-film)] uppercase select-none",
        variants[variant],
        sizes[size],
        className
      )}
      {...props}
    >
      {children}
    </span>
  );
};
