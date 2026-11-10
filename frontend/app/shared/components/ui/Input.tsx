import React, { InputHTMLAttributes, forwardRef, useId } from "react";
import { AlertCircle } from "lucide-react";
import { cn } from "../../utils/cn";

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  labelRight?: React.ReactNode;
  error?: string;
  helperText?: React.ReactNode;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, label, labelRight, error, helperText, leftIcon, rightIcon, id, ...props }, ref) => {
    const generatedId = useId();
    const inputId = id || generatedId;
    const messageId = `${inputId}-message`;

    return (
      <div className="w-full space-y-1.5">
        {(label || labelRight) && (
          <div className="flex items-center justify-between gap-2">
            {label && (
              <label
                htmlFor={inputId}
                className="block text-xs font-bold text-[#111111] uppercase tracking-wider"
              >
                {label}
                {props.required && <span className="text-[#E11D48] ml-1">*</span>}
              </label>
            )}
            {labelRight && <div>{labelRight}</div>}
          </div>
        )}
        <div className="relative flex items-center">
          {leftIcon && (
            <div className="absolute left-3.5 flex items-center pointer-events-none text-stone-400">
              {leftIcon}
            </div>
          )}
          <input
            id={inputId}
            ref={ref}
            aria-invalid={error ? true : undefined}
            aria-describedby={error || helperText ? messageId : undefined}
            className={cn(
              "w-full h-11 px-3.5 bg-white border border-stone-300 rounded-xl text-sm text-[#111111] font-medium placeholder-stone-400 transition-all focus:outline-none focus:border-[#D97706] focus:ring-4 focus:ring-amber-100/80 disabled:opacity-60 disabled:bg-stone-100",
              leftIcon && "pl-10",
              rightIcon && "pr-11",
              error &&
                "border-[#E11D48] focus:border-[#E11D48] focus:ring-rose-100/80 bg-rose-50/20",
              className
            )}
            {...props}
          />
          {rightIcon && (
            <div className="absolute right-3.5 flex items-center text-stone-400">
              {rightIcon}
            </div>
          )}
        </div>
        {error ? (
          <p id={messageId} className="flex items-center text-xs font-medium text-[#E11D48] gap-1 mt-1">
            <AlertCircle className="w-3.5 h-3.5 shrink-0" />
            <span>{error}</span>
          </p>
        ) : helperText ? (
          <p id={messageId} className="text-xs text-stone-500 mt-1">
            {helperText}
          </p>
        ) : null}
      </div>
    );
  }
);
Input.displayName = "Input";
