import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  type PresignedUrlDescriptor,
  useS3BatchUpload,
} from "@/shared/hooks/useS3BatchUpload";

interface MockXhrBehavior {
  onSend: (xhr: MockXMLHttpRequest, body: Document | XMLHttpRequestBodyInit | null | undefined) => void;
}

class MockXMLHttpRequest {
  public static behavior: MockXhrBehavior = {
    onSend: () => {},
  };
  public static instances: MockXMLHttpRequest[] = [];

  public method = "";
  public url = "";
  public async = true;
  public requestHeaders: Record<string, string> = {};
  public status = 0;
  public statusText = "";
  public aborted = false;

  public upload: {
    onprogress: ((event: ProgressEvent<EventTarget>) => void) | null;
  } = {
    onprogress: null,
  };

  public onload: (() => void) | null = null;
  public onerror: (() => void) | null = null;
  public onabort: (() => void) | null = null;

  constructor() {
    MockXMLHttpRequest.instances.push(this);
  }

  open(method: string, url: string, async = true) {
    this.method = method;
    this.url = url;
    this.async = async;
  }

  setRequestHeader(name: string, value: string) {
    this.requestHeaders[name] = value;
  }

  send(body?: Document | XMLHttpRequestBodyInit | null) {
    MockXMLHttpRequest.behavior.onSend(this, body);
  }

  abort() {
    this.aborted = true;
    this.onabort?.();
  }
}

describe("FE-C04 useS3BatchUpload Hook (4-Stream Direct S3/MinIO Upload)", () => {
  const originalXhr = globalThis.XMLHttpRequest;

  beforeEach(() => {
    MockXMLHttpRequest.instances = [];
    globalThis.XMLHttpRequest = MockXMLHttpRequest as unknown as typeof XMLHttpRequest;
  });

  afterEach(() => {
    globalThis.XMLHttpRequest = originalXhr;
    vi.restoreAllMocks();
  });

  it("uploads 10 frames with strictly <= 4 concurrent XHR PUT streams, tracks progress, and confirms sorted S3 keys", async () => {
    let activeInFlight = 0;
    let peakConcurrency = 0;

    MockXMLHttpRequest.behavior = {
      onSend: (xhr) => {
        activeInFlight += 1;
        peakConcurrency = Math.max(peakConcurrency, activeInFlight);

        setTimeout(() => {
          xhr.upload.onprogress?.({
            lengthComputable: true,
            loaded: 50,
            total: 100,
          } as ProgressEvent<EventTarget>);

          setTimeout(() => {
            activeInFlight -= 1;
            xhr.status = 200;
            xhr.statusText = "OK";
            xhr.onload?.();
          }, 10);
        }, 5);
      },
    };

    const files = Array.from({ length: 10 }, (_, idx) => {
      const frameNo = idx + 1;
      return new File([`scan-frame-${frameNo}`], `frame_${String(frameNo).padStart(2, "0")}.jpg`, {
        type: "image/jpeg",
      });
    });

    const fetchPresignedUrls = vi.fn(async (meta): Promise<PresignedUrlDescriptor[]> =>
      meta.map((m: { frameNo: number }) => ({
        frameNo: m.frameNo,
        uploadUrl: `https://minio.analoghub.local/presigned/frame-${m.frameNo}`,
        s3Key: `private/tenants/lab-1/rolls/roll-1/frames/${String(m.frameNo).padStart(2, "0")}_original.jpg`,
      }))
    );

    const onCompleteConfirm = vi.fn(async () => {});

    const { result } = renderHook(() => useS3BatchUpload());

    await act(async () => {
      await result.current.uploadBatch(files, fetchPresignedUrls, onCompleteConfirm);
    });

    // Xác minh giới hạn đúng tối đa 4 luồng song song (4 workers)
    expect(peakConcurrency).toBe(4);
    expect(MockXMLHttpRequest.instances).toHaveLength(10);
    expect(MockXMLHttpRequest.instances[0].method).toBe("PUT");
    expect(MockXMLHttpRequest.instances[0].requestHeaders["Content-Type"]).toBe("image/jpeg");

    // Toàn bộ 10 frames đạt 100% và gọi confirm với danh sách frameNo từ 1..10
    expect(result.current.isUploading).toBe(false);
    expect(result.current.overallPercent).toBe(100);
    expect(result.current.itemsProgress.every((item) => item.status === "SUCCESS")).toBe(true);
    expect(onCompleteConfirm).toHaveBeenCalledTimes(1);
    expect(onCompleteConfirm).toHaveBeenCalledWith(
      files.map((_, idx) => ({
        frameNo: idx + 1,
        s3Key: `private/tenants/lab-1/rolls/roll-1/frames/${String(idx + 1).padStart(2, "0")}_original.jpg`,
      }))
    );
  });

  it("marks failed frame as ERROR and skips confirm callback when S3 returns 500", async () => {
    MockXMLHttpRequest.behavior = {
      onSend: (xhr) => {
        setTimeout(() => {
          if (xhr.url.endsWith("frame-2")) {
            xhr.status = 500;
            xhr.statusText = "Internal Server Error";
          } else {
            xhr.status = 200;
            xhr.statusText = "OK";
          }
          xhr.onload?.();
        }, 5);
      },
    };

    const files = [
      new File(["1"], "01.jpg", { type: "image/jpeg" }),
      new File(["2"], "02.jpg", { type: "image/jpeg" }),
    ];

    const onCompleteConfirm = vi.fn(async () => {});
    const { result } = renderHook(() => useS3BatchUpload());

    await act(async () => {
      await expect(
        result.current.uploadBatch(
          files,
          async (meta) =>
            meta.map((m) => ({
              frameNo: m.frameNo,
              uploadUrl: `https://minio.analoghub.local/frame-${m.frameNo}`,
              s3Key: `key-${m.frameNo}`,
            })),
          onCompleteConfirm
        )
      ).rejects.toThrow(/S3/i);
    });

    expect(result.current.isUploading).toBe(false);
    expect(onCompleteConfirm).not.toHaveBeenCalled();
    const failedItem = result.current.itemsProgress.find((i) => i.frameNo === 2);
    expect(failedItem?.status).toBe("ERROR");
    expect(failedItem?.errorMessage).toContain("500");
  });

  it("aborts in-flight XHR uploads cleanly when cancelAll() is invoked", async () => {
    MockXMLHttpRequest.behavior = {
      onSend: () => {
        // Giữ kết nối treo để kiểm thử hủy chủ động bằng cancelAll()
      },
    };

    const files = [
      new File(["1"], "01.jpg", { type: "image/jpeg" }),
      new File(["2"], "02.jpg", { type: "image/jpeg" }),
      new File(["3"], "03.jpg", { type: "image/jpeg" }),
    ];

    const onCompleteConfirm = vi.fn(async () => {});
    const { result } = renderHook(() => useS3BatchUpload());

    let uploadPromise!: Promise<void>;
    await act(async () => {
      uploadPromise = result.current.uploadBatch(
        files,
        async (meta) =>
          meta.map((m) => ({
            frameNo: m.frameNo,
            uploadUrl: `https://minio.analoghub.local/frame-${m.frameNo}`,
            s3Key: `key-${m.frameNo}`,
          })),
        onCompleteConfirm
      );
      await Promise.resolve();
    });

    expect(MockXMLHttpRequest.instances).toHaveLength(3);

    await act(async () => {
      result.current.cancelAll();
      await uploadPromise.catch(() => {});
    });

    expect(MockXMLHttpRequest.instances.every((xhr) => xhr.aborted)).toBe(true);
    expect(result.current.isUploading).toBe(false);
    expect(onCompleteConfirm).not.toHaveBeenCalled();
  });
});
