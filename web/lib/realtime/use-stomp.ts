"use client";

import { Client, ReconnectionTimeMode, type IMessage, type StompSubscription } from "@stomp/stompjs";
import { useEffect, useRef, useState } from "react";
import { API_URL } from "@/lib/api/client";

export type SocketStatus = "connecting" | "connected" | "offline" | "denied";

type Options = {
  /** Topic to subscribe to; null = stay disconnected. */
  destination: string | null;
  /** Called before every (re)connect so an expired access token is refreshed first. null = can't connect. */
  connectHeaders: () => Promise<Record<string, string> | null>;
  onMessage: (body: unknown) => void;
  /** Called on every successful (re)connect, e.g. to refetch whatever was missed while offline. */
  onConnected?: () => void;
};

/**
 * STOMP over SockJS to the backend's /ws endpoint with automatic reconnect: exponential backoff
 * from 1 s up to 30 s. If the server refuses access (ERROR frame "Access denied"/"Unauthorized")
 * it stops retrying and reports "denied", so the caller can fall back to polling.
 */
export function useStomp({ destination, connectHeaders, onMessage, onConnected }: Options): SocketStatus {
  // Status is tracked per destination, so switching topics reads as "connecting" until the new
  // connection reports in (no state reset needed inside the effect).
  const [state, setState] = useState<{ destination: string | null; status: SocketStatus }>({ destination: null, status: "connecting" });
  // Latest callbacks without reconnecting when their identity changes.
  const callbacks = useRef({ connectHeaders, onMessage, onConnected });
  useEffect(() => {
    callbacks.current = { connectHeaders, onMessage, onConnected };
  });

  useEffect(() => {
    if (!destination) return;
    let subscription: StompSubscription | null = null;
    let cancelled = false;
    const setStatus = (update: SocketStatus | ((s: SocketStatus) => SocketStatus)) =>
      setState((prev) => {
        const current = prev.destination === destination ? prev.status : "connecting";
        return { destination, status: typeof update === "function" ? update(current) : update };
      });

    const client = new Client({
      webSocketFactory: () => {
        // sockjs-client expects Node's `global`; alias it in the browser before loading.
        (globalThis as { global?: unknown }).global ??= globalThis;
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        const SockJS = require("sockjs-client");
        return new SockJS(`${API_URL}/ws`);
      },
      reconnectDelay: 1_000,
      maxReconnectDelay: 30_000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      beforeConnect: async (c) => {
        const headers = await callbacks.current.connectHeaders();
        if (!headers) {
          setStatus("denied");
          await c.deactivate();
          return;
        }
        c.connectHeaders = headers;
      },
      onConnect: () => {
        if (cancelled) return;
        subscription = client.subscribe(destination, (message: IMessage) => {
          try {
            callbacks.current.onMessage(JSON.parse(message.body));
          } catch {
            // Ignore malformed frames; the next refetch corrects state.
          }
        });
        setStatus("connected");
        callbacks.current.onConnected?.();
      },
      onStompError: (frame) => {
        const reason = frame.headers.message ?? "";
        if (/Access denied|Unauthorized/i.test(reason)) {
          setStatus("denied");
          void client.deactivate();
        }
      },
      onWebSocketClose: () => {
        if (!cancelled) setStatus((s) => (s === "denied" ? s : "offline"));
      },
    });

    client.activate();
    return () => {
      cancelled = true;
      subscription?.unsubscribe();
      void client.deactivate();
    };
  }, [destination]);

  return state.destination === destination ? state.status : "connecting";
}
