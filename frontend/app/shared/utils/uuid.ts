const UUID_V7_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

let lastTimestampMs = 0;
let monotonicSeq = 0;

function fillRandomBytes(buffer: Uint8Array): void {
  if (typeof globalThis.crypto !== "undefined" && typeof globalThis.crypto.getRandomValues === "function") {
    globalThis.crypto.getRandomValues(buffer);
    return;
  }
  for (let i = 0; i < buffer.length; i += 1) {
    buffer[i] = Math.floor(Math.random() * 256);
  }
}

/**
 * Sinh chuỗi định danh chuẩn RFC 9562 UUIDv7 (time-ordered epoch milliseconds + CSPRNG).
 * Đảm bảo tính đơn điệu (monotonic) ngay cả khi sinh hàng loạt trong cùng 1 mili-giây.
 */
export function generateUuidV7(nowMs: number = Date.now()): string {
  const bytes = new Uint8Array(16);
  fillRandomBytes(bytes);

  let timestamp = Math.max(0, Math.floor(nowMs));
  if (timestamp === lastTimestampMs) {
    monotonicSeq = (monotonicSeq + 1) & 0x0fff;
    if (monotonicSeq === 0) {
      timestamp += 1;
    }
  } else {
    lastTimestampMs = timestamp;
    monotonicSeq = ((bytes[6] & 0x0f) << 8) | bytes[7];
  }

  // 48-bit big-endian Unix timestamp (ms)
  const high16 = Math.floor(timestamp / 0x100000000);
  const low32 = timestamp >>> 0;
  bytes[0] = (high16 >>> 8) & 0xff;
  bytes[1] = high16 & 0xff;
  bytes[2] = (low32 >>> 24) & 0xff;
  bytes[3] = (low32 >>> 16) & 0xff;
  bytes[4] = (low32 >>> 8) & 0xff;
  bytes[5] = low32 & 0xff;

  // Version 7 (0111) + 12-bit monotonic sequence (rand_a)
  bytes[6] = 0x70 | ((monotonicSeq >>> 8) & 0x0f);
  bytes[7] = monotonicSeq & 0xff;

  // Variant RFC 9562 (10xx xxxx)
  bytes[8] = (bytes[8] & 0x3f) | 0x80;

  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`;
}

export function isUuidV7(value: string): boolean {
  return UUID_V7_PATTERN.test(value);
}
