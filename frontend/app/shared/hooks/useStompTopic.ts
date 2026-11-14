import { useEffect, useRef } from "react";
import { StompManager } from "../api/stompManager";

export function useStompTopic<T>(
  destination: string | null | undefined,
  onMessage: (payload: T) => void,
  enabled: boolean = true
): void {
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;

  useEffect(() => {
    if (!destination || !enabled) return;

    const manager = StompManager.getInstance();
    const subscription = manager.subscribe(destination, (frame) => {
      try {
        const data = JSON.parse(frame.body) as T;
        onMessageRef.current(data);
      } catch (err) {
        console.error(`Lỗi parse STOMP message từ topic ${destination}:`, err);
      }
    });

    return () => {
      subscription.unsubscribe();
    };
  }, [destination, enabled]);
}
