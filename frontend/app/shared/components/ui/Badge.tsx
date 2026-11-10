import React from "react";
import { cn } from "../../utils/cn";

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: "amber" | "emerald" | "red" | "indigo" | "neutral" | "outline";
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
    amber: "bg-[#FFF7ED] text-kodak-gold border border-[#FED7AA]",
    emerald: "bg-[#ECFDF5] text-fuji-emerald border border-[#A7F3D0]",
    red: "bg-[#FFF1F2] text-safelight-red border border-[#FECDD3]",
    indigo: "bg-[#EEF2FF] text-[#4F46E5] border border-[#C7D2FE]",
    neutral: "bg-stone-100 text-stone-700 border border-stone-200",
    outline: "bg-white text-stone-600 border border-stone-300",
  };

  const sizes = {
    sm: "text-[11px] px-2 py-0.5 tracking-wide font-semibold rounded-md",
    md: "text-xs px-2.5 py-0.5 font-semibold rounded-full",
  };

  return (
    <span
      className={cn(
        "inline-flex items-center justify-center select-none",
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
