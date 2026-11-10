import React, { useEffect, useRef, useState } from "react";
import { Check, Move, RotateCcw, ZoomIn, ZoomOut } from "lucide-react";
import { Button } from "@/shared/components/ui/Button";
import { Modal } from "@/shared/components/ui/Modal";

export interface AvatarCropModalProps {
  isOpen: boolean;
  file: File | null;
  onClose: () => void;
  onConfirmCrop: (croppedFile: File, previewUrl: string) => void;
  isUploading?: boolean;
}

const VIEWPORT_SIZE = 256;
const OUTPUT_SIZE = 512;

export const AvatarCropModal: React.FC<AvatarCropModalProps> = ({
  isOpen,
  file,
  onClose,
  onConfirmCrop,
  isUploading = false,
}) => {
  const imgRef = useRef<HTMLImageElement | null>(null);
  const [imageSrc, setImageSrc] = useState<string>("");
  const [naturalSize, setNaturalSize] = useState<{ width: number; height: number }>({
    width: 0,
    height: 0,
  });

  const [zoom, setZoom] = useState<number>(1);
  const [offset, setOffset] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const dragStartRef = useRef<{ startX: number; startY: number; origX: number; origY: number }>({
    startX: 0,
    startY: 0,
    origX: 0,
    origY: 0,
  });

  useEffect(() => {
    if (!file) {
      setImageSrc("");
      return;
    }
    setZoom(1);
    setOffset({ x: 0, y: 0 });

    if (typeof URL !== "undefined" && typeof URL.createObjectURL === "function") {
      const objectUrl = URL.createObjectURL(file);
      setImageSrc(objectUrl);
      return () => {
        if (typeof URL.revokeObjectURL === "function") {
          URL.revokeObjectURL(objectUrl);
        }
      };
    } else {
      setImageSrc(`blob:mock-${file.name}`);
    }
  }, [file]);

  const handleImageLoad = (e: React.SyntheticEvent<HTMLImageElement>) => {
    const target = e.currentTarget;
    setNaturalSize({
      width: target.naturalWidth || VIEWPORT_SIZE,
      height: target.naturalHeight || VIEWPORT_SIZE,
    });
  };

  const handlePointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(true);
    dragStartRef.current = {
      startX: e.clientX,
      startY: e.clientY,
      origX: offset.x,
      origY: offset.y,
    };
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!isDragging) return;
    const dx = e.clientX - dragStartRef.current.startX;
    const dy = e.clientY - dragStartRef.current.startY;
    setOffset({
      x: Math.max(-180, Math.min(180, dragStartRef.current.origX + dx)),
      y: Math.max(-180, Math.min(180, dragStartRef.current.origY + dy)),
    });
  };

  const handlePointerUp = () => {
    setIsDragging(false);
  };

  const handleReset = () => {
    setZoom(1);
    setOffset({ x: 0, y: 0 });
  };

  const handleConfirm = async () => {
    if (!file) return;

    const imgEl = imgRef.current;
    if (!imgEl || !naturalSize.width || !naturalSize.height) {
      onConfirmCrop(file, imageSrc);
      return;
    }

    try {
      const canvas = document.createElement("canvas");
      canvas.width = OUTPUT_SIZE;
      canvas.height = OUTPUT_SIZE;
      const ctx = canvas.getContext("2d");

      if (!ctx) {
        onConfirmCrop(file, imageSrc);
        return;
      }

      const baseScale = Math.max(
        VIEWPORT_SIZE / naturalSize.width,
        VIEWPORT_SIZE / naturalSize.height
      );
      const totalScale = baseScale * zoom;
      const ratioToOutput = OUTPUT_SIZE / VIEWPORT_SIZE;

      const drawWidth = naturalSize.width * totalScale * ratioToOutput;
      const drawHeight = naturalSize.height * totalScale * ratioToOutput;

      const drawX = (OUTPUT_SIZE - drawWidth) / 2 + offset.x * ratioToOutput;
      const drawY = (OUTPUT_SIZE - drawHeight) / 2 + offset.y * ratioToOutput;

      ctx.fillStyle = "#FAF8F5";
      ctx.fillRect(0, 0, OUTPUT_SIZE, OUTPUT_SIZE);
      ctx.drawImage(imgEl, drawX, drawY, drawWidth, drawHeight);

      const outputMime =
        file.type === "image/png" || file.type === "image/webp" ? file.type : "image/jpeg";
      const ext = outputMime === "image/png" ? "png" : outputMime === "image/webp" ? "webp" : "jpg";

      const blob: Blob | null = await new Promise((resolve) =>
        canvas.toBlob((b) => resolve(b), outputMime, 0.92)
      );

      if (!blob) {
        onConfirmCrop(file, imageSrc);
        return;
      }

      const baseName = file.name.replace(/\.[^/.]+$/, "") || "avatar";
      const croppedFile = new File([blob], `${baseName}_cropped.${ext}`, {
        type: outputMime,
      });
      const croppedPreviewUrl =
        typeof URL !== "undefined" && typeof URL.createObjectURL === "function"
          ? URL.createObjectURL(croppedFile)
          : imageSrc;

      onConfirmCrop(croppedFile, croppedPreviewUrl);
    } catch {
      onConfirmCrop(file, imageSrc);
    }
  };

  const baseScale =
    naturalSize.width > 0 && naturalSize.height > 0
      ? Math.max(VIEWPORT_SIZE / naturalSize.width, VIEWPORT_SIZE / naturalSize.height)
      : 1;
  const renderedWidth =
    naturalSize.width > 0 ? naturalSize.width * baseScale * zoom : VIEWPORT_SIZE * zoom;
  const renderedHeight =
    naturalSize.height > 0 ? naturalSize.height * baseScale * zoom : VIEWPORT_SIZE * zoom;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Căn Chỉnh & Cắt Ảnh Đại Diện"
      description="Kéo trực tiếp trên ảnh hoặc dùng thanh trượt để chọn vùng hiển thị ưng ý nhất."
      footer={
        <>
          <Button type="button" variant="ghost" size="sm" onClick={handleReset}>
            <RotateCcw className="w-3.5 h-3.5 mr-1" />
            Đặt lại
          </Button>
          <Button type="button" variant="secondary" size="sm" onClick={onClose}>
            Hủy
          </Button>
          <Button
            type="button"
            variant="primary"
            size="sm"
            isLoading={isUploading}
            leftIcon={<Check className="w-4 h-4 text-[#F59E0B]" />}
            onClick={handleConfirm}
            data-testid="confirm-crop-avatar-btn"
          >
            Áp dụng &amp; Lưu ảnh đại diện
          </Button>
        </>
      }
    >
      <div className="space-y-5">
        <div className="flex flex-col items-center gap-2">
          <div
            onPointerDown={handlePointerDown}
            onPointerMove={handlePointerMove}
            onPointerUp={handlePointerUp}
            onPointerLeave={handlePointerUp}
            style={{ width: VIEWPORT_SIZE, height: VIEWPORT_SIZE }}
            className="relative overflow-hidden rounded-full border-2 border-[#D97706] ring-4 ring-amber-50 bg-stone-100 cursor-grab active:cursor-grabbing select-none touch-none flex items-center justify-center shadow-md"
          >
            {imageSrc && (
              <img
                ref={imgRef}
                src={imageSrc}
                alt="Xem trước vùng cắt ảnh đại diện"
                onLoad={handleImageLoad}
                draggable={false}
                style={{
                  width: `${renderedWidth}px`,
                  height: `${renderedHeight}px`,
                  maxWidth: "none",
                  transform: `translate3d(${offset.x}px, ${offset.y}px, 0)`,
                }}
                className="pointer-events-none select-none object-cover"
              />
            )}

            <div className="absolute inset-0 pointer-events-none border border-white/40 rounded-full">
              <div className="w-full h-full grid grid-cols-3 grid-rows-3">
                <div className="border-r border-b border-white/30" />
                <div className="border-r border-b border-white/30" />
                <div className="border-b border-white/30" />
                <div className="border-r border-b border-white/30" />
                <div className="border-r border-b border-white/30" />
                <div className="border-b border-white/30" />
                <div className="border-r border-white/30" />
                <div className="border-r border-white/30" />
                <div />
              </div>
            </div>
          </div>

          <span className="inline-flex items-center gap-1.5 text-[11px] text-stone-500">
            <Move className="w-3.5 h-3.5 text-[#D97706]" />
            Giữ chuột kéo ảnh hoặc điều chỉnh các thanh trượt bên dưới
          </span>
        </div>

        <div className="space-y-3 p-4 rounded-xl bg-[#FAF8F5] border border-stone-200">
          <div className="space-y-1">
            <div className="flex items-center justify-between text-xs text-stone-700">
              <span className="inline-flex items-center gap-1.5 font-semibold">
                <ZoomIn className="w-3.5 h-3.5 text-[#D97706]" />
                Thu phóng (Zoom)
              </span>
              <span className="font-mono font-bold text-[#D97706]">{Math.round(zoom * 100)}%</span>
            </div>
            <div className="flex items-center gap-2">
              <ZoomOut className="w-3.5 h-3.5 text-stone-400 shrink-0" />
              <input
                type="range"
                aria-label="Thu phóng ảnh đại diện"
                min={1}
                max={3}
                step={0.05}
                value={zoom}
                onChange={(e) => setZoom(Number(e.target.value))}
                className="w-full accent-[#D97706] cursor-pointer"
              />
              <ZoomIn className="w-3.5 h-3.5 text-stone-400 shrink-0" />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
            <div className="space-y-1">
              <div className="flex items-center justify-between text-[11px] text-stone-500">
                <span>Căn ngang (Trái / Phải)</span>
                <span className="font-mono">{offset.x}px</span>
              </div>
              <input
                type="range"
                aria-label="Căn ngang ảnh đại diện"
                min={-160}
                max={160}
                step={1}
                value={offset.x}
                onChange={(e) => setOffset((prev) => ({ ...prev, x: Number(e.target.value) }))}
                className="w-full accent-[#D97706] cursor-pointer"
              />
            </div>

            <div className="space-y-1">
              <div className="flex items-center justify-between text-[11px] text-stone-500">
                <span>Căn dọc (Lên / Xuống)</span>
                <span className="font-mono">{offset.y}px</span>
              </div>
              <input
                type="range"
                aria-label="Căn dọc ảnh đại diện"
                min={-160}
                max={160}
                step={1}
                value={offset.y}
                onChange={(e) => setOffset((prev) => ({ ...prev, y: Number(e.target.value) }))}
                className="w-full accent-[#D97706] cursor-pointer"
              />
            </div>
          </div>
        </div>
      </div>
    </Modal>
  );
};
