"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { components } from "@/lib/api/schema";
import { formatDateTime } from "@/lib/format";

type Pairing = components["schemas"]["PairingResponse"];

/** Owner only: pair a recording phone with each kitchen station. */
export function StationsDrawer({ restaurantId, onClose }: { restaurantId: string; onClose: () => void }) {
  const queryClient = useQueryClient();
  const stations = useQuery({
    queryKey: ["kds-stations", restaurantId],
    queryFn: () => unwrap(api.GET("/api/owner/restaurants/{restaurantId}/stations", { params: { path: { restaurantId } } })),
    refetchInterval: 5_000, // shows "paired" as soon as the phone scans the code
  });
  const [pairing, setPairing] = useState<{ response: Pairing; shownAtMs: number } | null>(null);
  const start = useMutation({
    mutationFn: (stationId: string) =>
      unwrap(api.POST("/api/owner/restaurants/{restaurantId}/stations/{stationId}/pairing", { params: { path: { restaurantId, stationId } } })),
    onSuccess: (response) => setPairing({ response, shownAtMs: Date.now() }),
  });

  // The phone has paired once the station reports a pairing newer than this QR.
  const pairedAt = pairing && stations.data?.find((s) => s.id === pairing.response.stationId)?.devicePairedAt;
  const pairedNow = !!pairedAt && Date.parse(pairedAt) >= pairing!.shownAtMs - 5_000;

  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-black/60" onClick={onClose}>
      <aside className="flex h-full w-full max-w-xl flex-col bg-stone-900 shadow-2xl" onClick={(e) => e.stopPropagation()} aria-label="Stations">
        <header className="flex items-center justify-between border-b border-stone-800 p-4">
          <h2 className="text-2xl font-bold">Station phones</h2>
          <button onClick={onClose} className="min-h-12 rounded-xl bg-stone-800 px-4 text-lg font-semibold">Close</button>
        </header>
        {pairing && pairedNow ? (
          <div className="flex flex-1 flex-col items-center justify-center gap-4 p-6 text-center">
            <p className="text-3xl font-bold text-green-400">✓ Paired</p>
            <p className="text-lg text-stone-300">The {pairing.response.stationName} phone is ready to record.</p>
            <button
              onClick={() => {
                setPairing(null);
                void queryClient.invalidateQueries({ queryKey: ["kds-stations", restaurantId] });
              }}
              className="min-h-12 rounded-xl bg-stone-800 px-6 text-lg"
            >
              Done
            </button>
          </div>
        ) : pairing ? (
          <PairingQr pairing={pairing.response} onDone={() => setPairing(null)} />
        ) : (
          <div className="flex-1 space-y-3 overflow-y-auto p-4">
            {start.isError && <p className="rounded-xl bg-red-950 px-4 py-3 text-red-200">{errorMessage(start.error)}</p>}
            {stations.isPending ? (
              <p className="text-stone-400">Loading…</p>
            ) : stations.isError ? (
              <p className="text-red-300">{errorMessage(stations.error)}</p>
            ) : stations.data.length === 0 ? (
              <p className="py-12 text-center text-stone-500">No stations yet. Create one first.</p>
            ) : (
              stations.data.map((s) => (
                <div key={s.id} className="flex items-center justify-between gap-3 rounded-2xl bg-stone-800 p-4">
                  <div>
                    <p className="text-xl font-bold">{s.name}</p>
                    <p className="text-stone-400">{s.devicePairedAt ? `Phone paired ${formatDateTime(s.devicePairedAt)}` : "No phone paired"}</p>
                  </div>
                  <button
                    disabled={start.isPending}
                    onClick={() => start.mutate(s.id)}
                    className="min-h-14 rounded-xl bg-brand-600 px-5 text-lg font-bold text-white disabled:opacity-50"
                  >
                    {s.devicePairedAt ? "Re-pair" : "Pair phone"}
                  </button>
                </div>
              ))
            )}
          </div>
        )}
      </aside>
    </div>
  );
}

function PairingQr({ pairing, onDone }: { pairing: Pairing; onDone: () => void }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1_000);
    return () => clearInterval(timer);
  }, []);
  const left = Math.max(0, Math.floor((Date.parse(pairing.expiresAt) - now) / 1000));

  return (
    <div className="flex flex-1 flex-col items-center gap-4 overflow-y-auto p-6 text-center">
      <p className="text-xl font-bold">Scan with the {pairing.stationName} phone</p>
      {left > 0 ? (
        <img src={pairing.qrSvgDataUri} alt="Pairing QR code" className="w-full max-w-sm rounded-2xl bg-white p-2" />
      ) : (
        <p className="py-16 text-lg text-stone-400">This code has expired.</p>
      )}
      <p className="tabular-nums text-stone-300">
        {left > 0 ? `Expires in ${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")} · works once` : "Close and tap Pair phone again."}
      </p>
      <p className="text-sm text-stone-500">Pairing replaces any phone already paired with this station.</p>
      <button onClick={onDone} className="min-h-12 rounded-xl bg-stone-800 px-6 text-lg">Back</button>
    </div>
  );
}
