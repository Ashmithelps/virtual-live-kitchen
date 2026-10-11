"use client";

import { useState } from "react";
import type { Order } from "@/lib/api/types";
import { REJECT_REASONS, actionsFor, fulfilmentLabel, minutesSince, urgency, waitingHint, type Action, type KdsRole } from "@/lib/kds/board";

const AGE_STYLE = {
  normal: "bg-stone-700 text-stone-100",
  amber: "bg-amber-500 text-stone-950",
  red: "bg-red-600 text-white animate-pulse",
};

const ACTION_STYLE = {
  primary: "bg-brand-600 active:bg-brand-700 text-white",
  success: "bg-green-600 active:bg-green-700 text-white",
  danger: "bg-stone-700 active:bg-red-700 text-red-200",
};

const TYPE_STYLE = {
  DINE_IN: "bg-sky-900 text-sky-100",
  TAKEAWAY: "bg-violet-900 text-violet-100",
  DELIVERY: "bg-emerald-900 text-emerald-100",
};

export function OrderCard({
  order,
  role,
  now,
  flashing,
  busy,
  onAction,
}: {
  order: Order;
  role: KdsRole;
  now: number;
  flashing: boolean;
  busy: boolean;
  onAction: (action: Action, reason?: string) => void;
}) {
  const [rejecting, setRejecting] = useState(false);
  const minutes = minutesSince(order.timeline.placedAt, now);
  const actions = actionsFor(order, role);
  const hint = waitingHint(order, role);

  return (
    <article className={`rounded-2xl bg-stone-800 p-4 shadow-lg ${flashing ? "animate-kds-flash ring-4 ring-brand-500" : ""}`} aria-label={`Order ${order.displayNumber}`}>
      <header className="flex items-start justify-between gap-2">
        <div>
          <p className="text-5xl font-black leading-none tracking-tight">#{order.displayNumber}</p>
          <span className={`mt-2 inline-block rounded-lg px-2.5 py-1 text-base font-bold ${TYPE_STYLE[order.orderType]}`}>{fulfilmentLabel(order)}</span>
        </div>
        <span className={`rounded-xl px-3 py-1.5 text-xl font-bold tabular-nums ${AGE_STYLE[urgency(minutes)]}`}>{minutes} min</span>
      </header>

      <ul className="mt-3 space-y-1.5">
        {order.items.map((item, i) => (
          <li key={i} className="text-xl leading-snug">
            <span className="font-bold">{item.quantity} ×</span> {item.name}
            {item.notes && <span className="block pl-7 text-base font-semibold text-amber-300">↳ {item.notes}</span>}
          </li>
        ))}
      </ul>
      {order.notes && <p className="mt-2 rounded-lg bg-amber-950 px-3 py-2 text-base text-amber-200">Note: {order.notes}</p>}
      {order.preparation?.chefDisplayName && (
        <p className="mt-2 text-sm text-stone-400">
          {order.preparation.chefDisplayName} · {order.preparation.stationName}
        </p>
      )}
      {order.paymentStatus === "PAY_LATER" && <p className="mt-1 text-sm font-semibold text-amber-300">Bill unpaid · pay at table</p>}
      {order.paymentStatus === "REFUND_PENDING" && <p className="mt-1 text-sm text-stone-400">Refund in progress</p>}

      {rejecting ? (
        <div className="mt-4 space-y-2">
          <p className="text-sm text-stone-300">Reject because…</p>
          <div className="grid gap-2">
            {REJECT_REASONS.map((reason) => (
              <button key={reason} disabled={busy} onClick={() => onAction(actions.find((a) => a.needsReason)!, reason)} className="min-h-14 rounded-xl bg-red-700 text-lg font-semibold text-white active:bg-red-800 disabled:opacity-50">
                {reason}
              </button>
            ))}
            <button onClick={() => setRejecting(false)} className="min-h-12 rounded-xl text-stone-300">
              Back
            </button>
          </div>
        </div>
      ) : actions.length > 0 ? (
        <div className={`mt-4 grid gap-2 ${actions.length > 1 ? "grid-cols-2" : ""}`}>
          {actions.map((action) => (
            <button
              key={action.status}
              disabled={busy}
              onClick={() => (action.needsReason ? setRejecting(true) : onAction(action))}
              className={`min-h-16 rounded-xl text-2xl font-bold disabled:opacity-50 ${ACTION_STYLE[action.tone]}`}
            >
              {busy ? "…" : action.label}
            </button>
          ))}
        </div>
      ) : (
        hint && <p className="mt-4 rounded-xl border border-dashed border-stone-600 px-3 py-3 text-center text-stone-400">{hint}</p>
      )}
    </article>
  );
}
