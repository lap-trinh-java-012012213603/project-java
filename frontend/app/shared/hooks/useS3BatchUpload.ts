import { useCallback, useRef, useState } from "react";

export interface UploadFileMetadata {
  frameNo: number;
  fileName: string;
  contentType: string;
  fileSizeBytes: number;
}

export interface PresignedUrlDescriptor {
  frameNo: number;
  uploadUrl: string;
  s3Key: string;
}

export interface CompletedUploadFrame {
  frameNo: number;
  s3Key: string;
}

export interface BatchItemProgress {
  frameNo: number;
  fileName: string;
  fileSizeBytes: number;
  progressPercent: number;
  status: "PENDING" | "UPLOADING" | "SUCCESS" | "ERROR";
  s3Key?: string;
  errorMessage?: string;
}

const MAX_CONCURRENT_UPLOADS = 4;

export function useS3BatchUpload() {
  const [itemsProgress, setItemsProgress] = useState<BatchItemProgress[]>([]);
  const [overallPercent, setOverallPercent] = useState<number>(0);
  const [isUploading, setIsUploading] = useState<boolean>(false);
  const activeXhrsRef = useRef<XMLHttpRequest[]>([]);
  const isCancelledRef = useRef<boolean>(false);

  const cancelAll = useCallback(() => {
    isCancelledRef.current = true;
    const xhrsToAbort = [...activeXhrsRef.current];
    activeXhrsRef.current = [];
    xhrsToAbort.forEach((xhr) => xhr.abort());
    setIsUploading(false);
  }, []);

  const uploadBatch = useCallback(
    async (
      files: File[],
      fetchPresignedUrlsFn: (filesMeta: UploadFileMetadata[]) => Promise<PresignedUrlDescriptor[]>,
      onCompleteConfirmFn: (completedKeys: CompletedUploadFrame[]) => Promise<void>
    ) => {
      if (files.length === 0) {
        setItemsProgress([]);
        setOverallPercent(0);
        return;
      }

      isCancelledRef.current = false;
      setIsUploading(true);
      activeXhrsRef.current = [];

      // 1. Khởi tạo danh sách tiến độ ban đầu cho toàn bộ cuộn phim
      const initialProgress: BatchItemProgress[] = files.map((file, idx) => ({
        frameNo: idx + 1,
        fileName: file.name,
        fileSizeBytes: file.size,
        progressPercent: 0,
        status: "PENDING",
      }));
      setItemsProgress(initialProgress);
      setOverallPercent(0);

      const framePercents = new Array<number>(files.length).fill(0);
      const updateOverallFromFrames = () => {
        const sum = framePercents.reduce((acc, val) => acc + val, 0);
        setOverallPercent(Math.round(sum / files.length));
      };

      try {
        // 2. Lấy danh sách Presigned PUT URLs từ Backend
        const presignedList = await fetchPresignedUrlsFn(
          files.map((file, idx) => ({
            frameNo: idx + 1,
            fileName: file.name,
            contentType: file.type || "image/jpeg",
            fileSizeBytes: file.size,
          }))
        );

        const taskQueue = [...presignedList];
        const completedKeys: CompletedUploadFrame[] = [];

        // 3. Worker xử lý hàng đợi với giới hạn tối đa 4 luồng song song
        const runWorker = async (): Promise<void> => {
          while (!isCancelledRef.current && taskQueue.length > 0) {
            const task = taskQueue.shift();
            if (!task) break;

            const targetFile = files[task.frameNo - 1];
            if (!targetFile) {
              throw new Error(`Không tìm thấy tệp tương ứng cho frame ${task.frameNo}`);
            }

            setItemsProgress((prev) =>
              prev.map((item) =>
                item.frameNo === task.frameNo ? { ...item, status: "UPLOADING" } : item
              )
            );

            await new Promise<void>((resolve, reject) => {
              const xhr = new XMLHttpRequest();
              activeXhrsRef.current.push(xhr);

              xhr.open("PUT", task.uploadUrl, true);
              xhr.setRequestHeader("Content-Type", targetFile.type || "application/octet-stream");

              xhr.upload.onprogress = (event) => {
                if (event.lengthComputable) {
                  const percent = Math.round((event.loaded / event.total) * 100);
                  framePercents[task.frameNo - 1] = percent;
                  updateOverallFromFrames();
                  setItemsProgress((prev) =>
                    prev.map((item) =>
                      item.frameNo === task.frameNo
                        ? { ...item, progressPercent: percent }
                        : item
                    )
                  );
                }
              };

              xhr.onload = () => {
                activeXhrsRef.current = activeXhrsRef.current.filter((x) => x !== xhr);
                if (xhr.status >= 200 && xhr.status < 300) {
                  framePercents[task.frameNo - 1] = 100;
                  updateOverallFromFrames();
                  completedKeys.push({ frameNo: task.frameNo, s3Key: task.s3Key });
                  setItemsProgress((prev) =>
                    prev.map((item) =>
                      item.frameNo === task.frameNo
                        ? { ...item, status: "SUCCESS", progressPercent: 100, s3Key: task.s3Key }
                        : item
                    )
                  );
                  resolve();
                } else {
                  setItemsProgress((prev) =>
                    prev.map((item) =>
                      item.frameNo === task.frameNo
                        ? { ...item, status: "ERROR", errorMessage: `S3 Error ${xhr.status}` }
                        : item
                    )
                  );
                  reject(new Error(`Tải lên S3 thất bại (${xhr.status}): ${xhr.statusText}`));
                }
              };

              xhr.onerror = () => {
                activeXhrsRef.current = activeXhrsRef.current.filter((x) => x !== xhr);
                setItemsProgress((prev) =>
                  prev.map((item) =>
                    item.frameNo === task.frameNo
                      ? { ...item, status: "ERROR", errorMessage: "Lỗi kết nối mạng" }
                      : item
                  )
                );
                reject(new Error("Lỗi mạng khi tải lên S3"));
              };

              xhr.onabort = () => {
                activeXhrsRef.current = activeXhrsRef.current.filter((x) => x !== xhr);
                taskQueue.length = 0;
                setItemsProgress((prev) =>
                  prev.map((item) =>
                    item.frameNo === task.frameNo
                      ? { ...item, status: "ERROR", errorMessage: "Đã hủy tải lên" }
                      : item
                  )
                );
                reject(new Error("Đã hủy tiến trình tải lên S3"));
              };

              xhr.send(targetFile);
            });
          }
        };

        const workerCount = Math.min(MAX_CONCURRENT_UPLOADS, taskQueue.length);
        const workers = Array.from({ length: workerCount }, () => runWorker());
        await Promise.all(workers);

        // 4. Khi 100% frames hoàn tất, sắp xếp theo thứ tự frameNo và xác nhận lưu DB
        if (!isCancelledRef.current && completedKeys.length === files.length) {
          completedKeys.sort((a, b) => a.frameNo - b.frameNo);
          await onCompleteConfirmFn(completedKeys);
        }
      } finally {
        setIsUploading(false);
      }
    },
    []
  );

  return {
    itemsProgress,
    overallPercent,
    isUploading,
    uploadBatch,
    cancelAll,
  };
}
