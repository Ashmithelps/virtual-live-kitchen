"use client";

import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from "react";

type Tone = "success" | "error" | "info";
type Toast = { id: number; tone: Tone; message: string };

type ToastApi = { success(message: string): void; error(message: string): void; info(message: string): void };

const ToastContext = createContext<ToastApi | null>(null);

const TONE: Record<Tone, string> = {
  success: "bg-green-700 text-white",
  error: "bg-red-700 text-white",
  info: "bg-stone-800 text-white",
};

/** Small transient notices for the result of an action ("Saved", "Couldn't delete …"). */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  const push = useCallback((tone: Tone, message: string) => {
    const id = nextId.current++;
    setToasts((all) => [...all.slice(-3), { id, tone, message }]);
    setTimeout(() => setToasts((all) => all.filter((t) => t.id !== id)), tone === "error" ? 6000 : 3500);
  }, []);

  const api = useMemo<ToastApi>(
    () => ({ success: (m) => push("success", m), error: (m) => push("error", m), info: (m) => push("info", m) }),
    [push],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed inset-x-0 bottom-4 z-50 flex flex-col items-center gap-2 px-4">
        {toasts.map((t) => (
          <div key={t.id} role={t.tone === "error" ? "alert" : "status"} className={`pointer-events-auto max-w-md rounded-xl px-4 py-3 text-sm font-medium shadow-lg ${TONE[t.tone]}`}>
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const value = useContext(ToastContext);
  if (!value) throw new Error("useToast must be used inside <ToastProvider>");
  return value;
}
