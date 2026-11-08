import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useAuthStore } from "../stores/useAuthStore";

const DEFAULT_WS_ENDPOINT =
  import.meta.env.VITE_WS_ENDPOINT ||
  import.meta.env.VITE_WS_BASE_URL ||
  "http://localhost:8080/ws";

const RECONNECT_DELAY_MS = 5000;
const HEARTBEAT_MS = 10000;

interface PendingSubscriptionEntry {
  destination: string;
  callback: (msg: IMessage) => void;
  subHolder: { sub: StompSubscription | null };
}

export class StompManager {
  private static instance: StompManager | null = null;
  private client: Client;
  private isConnected = false;
  private nextSubId = 0;
  private pendingSubscriptions: Map<string, PendingSubscriptionEntry> = new Map();

  private constructor(endpoint: string = DEFAULT_WS_ENDPOINT) {
    const isNativeWebSocketUrl = endpoint.startsWith("ws://") || endpoint.startsWith("wss://");

    this.client = new Client({
      ...(isNativeWebSocketUrl
        ? { brokerURL: endpoint }
        : { webSocketFactory: () => new SockJS(endpoint) }),
      reconnectDelay: RECONNECT_DELAY_MS,
      heartbeatIncoming: HEARTBEAT_MS,
      heartbeatOutgoing: HEARTBEAT_MS,
      beforeConnect: () => {
        const token = useAuthStore.getState().accessToken;
        this.client.connectHeaders = token
          ? { Authorization: `Bearer ${token}` }
          : {};
      },
      onConnect: () => {
        this.isConnected = true;
        // Đăng ký lại toàn bộ các topic đang hoạt động sau khi kết nối / auto-reconnect thành công
        this.pendingSubscriptions.forEach((item) => {
          item.subHolder.sub = this.client.subscribe(item.destination, item.callback);
        });
      },
      onDisconnect: () => {
        this.isConnected = false;
      },
      onWebSocketClose: () => {
        this.isConnected = false;
      },
      onStompError: (frame) => {
        console.error("STOMP Protocol Error:", frame.headers["message"], frame.body);
      },
    });

    if (typeof window !== "undefined") {
      this.client.activate();
    }
  }

  public static getInstance(endpoint?: string): StompManager {
    if (!StompManager.instance) {
      StompManager.instance = new StompManager(endpoint);
    }
    return StompManager.instance;
  }

  public getClient(): Client {
    return this.client;
  }

  public isSocketConnected(): boolean {
    return this.isConnected;
  }

  public subscribe(destination: string, callback: (message: IMessage) => void) {
    const subKey = `sub-${++this.nextSubId}`;
    const subHolder: { sub: StompSubscription | null } = { sub: null };

    if (this.isConnected) {
      subHolder.sub = this.client.subscribe(destination, callback);
    }

    this.pendingSubscriptions.set(subKey, { destination, callback, subHolder });

    return {
      unsubscribe: () => {
        if (subHolder.sub) {
          subHolder.sub.unsubscribe();
          subHolder.sub = null;
        }
        this.pendingSubscriptions.delete(subKey);
      },
    };
  }

  public publish(destination: string, body: unknown): void {
    if (this.isConnected) {
      this.client.publish({
        destination,
        body: JSON.stringify(body),
      });
    } else {
      console.warn("STOMP publish failed: Socket chưa kết nối.");
    }
  }

  public disconnect(): void {
    this.isConnected = false;
    this.pendingSubscriptions.clear();
    void this.client.deactivate();
    StompManager.instance = null;
  }

  public static resetForTesting(): void {
    if (StompManager.instance) {
      StompManager.instance.disconnect();
    }
  }
}
