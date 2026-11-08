import http from "node:http";
import { createRequire } from "node:module";
import type { AddressInfo } from "node:net";
import type { Duplex } from "node:stream";
import { act, render, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { StompManager } from "@/shared/api/stompManager";
import { useStompTopic } from "@/shared/hooks/useStompTopic";
import { useAuthStore } from "@/shared/stores/useAuthStore";

interface ServerWebSocketInstance {
  on(event: "message", listener: (ev: { data: string }) => void): void;
  send(data: string): boolean;
  close(): boolean;
}

interface FayeWebSocketStatic {
  new (
    request: http.IncomingMessage,
    socket: Duplex,
    body: Buffer,
    protocols?: string[]
  ): ServerWebSocketInstance;
  isWebSocket(request: http.IncomingMessage): boolean;
}

const require = createRequire(import.meta.url);
const FayeWebSocket = require("faye-websocket") as FayeWebSocketStatic;

function TestTopicSubscriber<T>({
  destination,
  onMessage,
  enabled = true,
}: {
  destination: string | null | undefined;
  onMessage: (payload: T) => void;
  enabled?: boolean;
}) {
  useStompTopic<T>(destination, onMessage, enabled);
  return <div>Subscriber</div>;
}

describe("FE-C04 StompManager Singleton & useStompTopic", () => {
  beforeEach(() => {
    localStorage.clear();
    useAuthStore.getState().logout();
    StompManager.resetForTesting();
  });

  afterEach(() => {
    StompManager.resetForTesting();
    vi.restoreAllMocks();
  });

  it("maintains a single StompManager instance, configures 5s auto-reconnect and attaches latest JWT Bearer header", async () => {
    useAuthStore.setState({ accessToken: "jwt-access-token-123", isAuthenticated: true });

    const first = StompManager.getInstance();
    const second = StompManager.getInstance();

    expect(first).toBe(second);

    const client = first.getClient();
    expect(client.reconnectDelay).toBe(5000);
    expect(client.heartbeatIncoming).toBe(10000);
    expect(client.heartbeatOutgoing).toBe(10000);

    // Gọi beforeConnect để mô phỏng lúc chuẩn bị bắt tay STOMP CONNECT
    await client.beforeConnect(client);
    expect(client.connectHeaders).toEqual({
      Authorization: "Bearer jwt-access-token-123",
    });

    // Khi accessToken được làm mới (refresh), lần reconnect tiếp theo phải đính kèm token mới nhất
    useAuthStore.setState({ accessToken: "jwt-refreshed-token-999", isAuthenticated: true });
    await client.beforeConnect(client);
    expect(client.connectHeaders).toEqual({
      Authorization: "Bearer jwt-refreshed-token-999",
    });
  });

  it("connects over real WebSocket STOMP /ws with Bearer JWT, receives topic messages, and auto-unsubscribes on unmount", async () => {
    const receivedFrames: string[] = [];
    const serverSockets: ServerWebSocketInstance[] = [];

    // Khởi tạo mock STOMP 1.2 WebSocket broker thật trên cổng nội bộ tại đường dẫn /ws
    const server = http.createServer();
    server.on("upgrade", (request, socket, body) => {
      if (!FayeWebSocket.isWebSocket(request) || request.url !== "/ws") {
        socket.destroy();
        return;
      }

      const ws = new FayeWebSocket(request, socket, body, ["v12.stomp", "v11.stomp", "v10.stomp"]);
      serverSockets.push(ws);

      ws.on("message", (event: { data: string }) => {
        const raw = String(event.data);
        receivedFrames.push(raw);

        if (raw.startsWith("CONNECT") || raw.startsWith("STOMP")) {
          // Phản hồi frame CONNECTED chuẩn STOMP 1.2
          ws.send("CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0");
        } else if (raw.startsWith("SUBSCRIBE")) {
          const idMatch = raw.match(/id:([^\n\r]+)/);
          const destMatch = raw.match(/destination:([^\n\r]+)/);
          const subId = idMatch ? idMatch[1] : "sub-0";
          const dest = destMatch ? destMatch[1] : "/topic/labs/lab-1/kanban";

          // Bắn thử 1 bản tin realtime xuống client ngay khi đăng ký thành công
          const payload = JSON.stringify({ rollId: "roll-36", newStatus: "SCANNING" });
          ws.send(
            `MESSAGE\nsubscription:${subId}\ndestination:${dest}\nmessage-id:msg-1\ncontent-type:application/json\n\n${payload}\0`
          );
        }
      });
    });

    await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", () => resolve()));
    const { port } = server.address() as AddressInfo;

    try {
      useAuthStore.setState({ accessToken: "live-stomp-jwt-777", isAuthenticated: true });
      const manager = StompManager.getInstance(`ws://127.0.0.1:${port}/ws`);
      expect(manager).toBe(StompManager.getInstance());

      const onMessage = vi.fn();
      const { unmount } = render(
        <TestTopicSubscriber<{ rollId: string; newStatus: string }>
          destination="/topic/labs/lab-1/kanban"
          onMessage={onMessage}
        />
      );

      // Xác nhận nhận được message STOMP từ server test thật
      await waitFor(
        () => {
          expect(onMessage).toHaveBeenCalledWith({
            rollId: "roll-36",
            newStatus: "SCANNING",
          });
        },
        { timeout: 4000 }
      );

      // Xác nhận frame CONNECT có đính kèm đúng header Authorization Bearer JWT
      const connectFrame = receivedFrames.find(
        (f) => f.startsWith("CONNECT") || f.startsWith("STOMP")
      );
      expect(connectFrame).toBeDefined();
      expect(connectFrame).toContain("Authorization:Bearer live-stomp-jwt-777");

      // Kiểm thử publish() gửi frame SEND thực tế lên server STOMP
      manager.publish("/app/chat.send", {
        conversationId: "conv-analog-01",
        content: "Máy Leica M6 cơ tốc còn chuẩn không bác?",
      });

      await waitFor(
        () => {
          const sendFrame = receivedFrames.find((f) => f.startsWith("SEND"));
          expect(sendFrame).toBeDefined();
          expect(sendFrame).toContain("destination:/app/chat.send");
          expect(sendFrame).toContain("conv-analog-01");
        },
        { timeout: 2000 }
      );

      // Unmount component -> useStompTopic phải tự động gửi lệnh UNSUBSCRIBE
      act(() => {
        unmount();
      });

      await waitFor(
        () => {
          expect(receivedFrames.some((f) => f.startsWith("UNSUBSCRIBE"))).toBe(true);
        },
        { timeout: 2000 }
      );
    } finally {
      StompManager.resetForTesting();
      serverSockets.forEach((ws) => ws.close());
      await new Promise<void>((resolve) => server.close(() => resolve()));
    }
  });

  it("supports multiple subscribers on the same topic and re-subscribes active ones upon reconnect", () => {
    const manager = StompManager.getInstance();
    const client = manager.getClient();

    const mockUnsubA = vi.fn();
    const mockUnsubB = vi.fn();
    let callCount = 0;

    const subscribeSpy = vi.spyOn(client, "subscribe").mockImplementation(() => {
      callCount += 1;
      return {
        id: `sub-${callCount}`,
        unsubscribe: callCount === 1 ? mockUnsubA : mockUnsubB,
      };
    });

    const cbA = vi.fn();
    const cbB = vi.fn();

    // Đăng ký khi socket chưa kết nối -> lưu vào hàng chờ pending
    const subA = manager.subscribe("/topic/orders/ord-1", cbA);
    const subB = manager.subscribe("/topic/orders/ord-1", cbB);
    expect(subscribeSpy).not.toHaveBeenCalled();

    // Giả lập kết nối thành công -> tự động đăng ký cả 2 subscriber
    client.onConnect({ command: "CONNECTED", headers: {}, body: "", isBinaryBody: false, binaryBody: new Uint8Array() });
    expect(subscribeSpy).toHaveBeenCalledTimes(2);

    // Hủy subA nhưng giữ lại subB
    subA.unsubscribe();
    expect(mockUnsubA).toHaveBeenCalledTimes(1);
    expect(mockUnsubB).not.toHaveBeenCalled();

    // Giả lập rớt mạng rồi auto-reconnect lại -> chỉ đăng ký lại subB còn hoạt động
    client.onDisconnect({ command: "DISCONNECT", headers: {}, body: "", isBinaryBody: false, binaryBody: new Uint8Array() });
    subscribeSpy.mockClear();

    client.onConnect({ command: "CONNECTED", headers: {}, body: "", isBinaryBody: false, binaryBody: new Uint8Array() });
    expect(subscribeSpy).toHaveBeenCalledTimes(1);
    expect(subscribeSpy).toHaveBeenCalledWith("/topic/orders/ord-1", cbB);

    subB.unsubscribe();
  });

  it("does not subscribe when useStompTopic is disabled or destination is null", () => {
    const manager = StompManager.getInstance();
    const subSpy = vi.spyOn(manager, "subscribe");

    render(<TestTopicSubscriber destination={null} onMessage={vi.fn()} />);
    render(
      <TestTopicSubscriber destination="/user/queue/notifications" onMessage={vi.fn()} enabled={false} />
    );

    expect(subSpy).not.toHaveBeenCalled();
  });
});
