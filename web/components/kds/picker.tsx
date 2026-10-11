"use client";

import { unlockSound } from "@/lib/kds/chime";

/** Big-button chooser (station for a chef, restaurant for an owner). */
export function Picker({
  title,
  subtitle,
  options,
  onPick,
  onLogout,
}: {
  title: string;
  subtitle?: string;
  options: { id: string; label: string; detail?: string }[];
  onPick: (id: string) => void;
  onLogout: () => void;
}) {
  return (
    <div className="mx-auto flex min-h-dvh max-w-4xl flex-col justify-center gap-6 p-6">
      <div>
        <h1 className="text-3xl font-bold">{title}</h1>
        {subtitle && <p className="mt-1 text-lg text-stone-400">{subtitle}</p>}
      </div>
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-3">
        {options.map((o) => (
          <button
            key={o.id}
            onClick={() => {
              unlockSound(); // a tap: lets the board play the new-order chime
              onPick(o.id);
            }}
            className="min-h-28 rounded-3xl bg-stone-800 p-5 text-left active:bg-brand-700"
          >
            <span className="block text-2xl font-bold">{o.label}</span>
            {o.detail && <span className="mt-1 block text-stone-400">{o.detail}</span>}
          </button>
        ))}
      </div>
      <button onClick={onLogout} className="self-start text-lg text-stone-400 underline">
        Log out
      </button>
    </div>
  );
}
