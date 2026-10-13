"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { errorMessage, unwrap } from "@/lib/api/errors";
import { stationApi, storePairing, usePairedStation } from "@/lib/station/device";
import { useHydrated } from "@/lib/storage";
import { StationRecorder } from "@/components/station/station-recorder";

/** One-time pairing code from the owner's QR link (/station#pair=<code>), read once. */
function readPairingCode(): string | null {
  if (typeof window === "undefined") return null;
  return new URLSearchParams(window.location.hash.slice(1)).get("pair");
}

/**
 * Station phone. Pairs by opening the owner's QR link, then records continuously and uploads
 * only the footage windows the backend asks for.
 */
export default function StationPage() {
  const hydrated = useHydrated();
  const station = usePairedStation();
  const [code] = useState(readPairingCode);

  const pairing = useQuery({
    queryKey: ["station-pair", code],
    enabled: !!code,
    retry: false,
    staleTime: Infinity,
    queryFn: async () => {
      // Remove the one-time code from the address bar and history right away.
      window.history.replaceState(null, "", "/station");
      const paired = await unwrap(stationApi.POST("/api/station-device/pair", { body: { pairingCode: code! } }));
      storePairing(paired);
      return paired.station;
    },
  });

  // Server and first client render must match ("Loading…"); pairing state is only shown after hydration.
  if (!hydrated) return <Centered title="Loading…" />;
  if (pairing.isFetching) return <Centered title="Pairing…" />;
  if (station) return <StationRecorder station={station} />;
  return (
    <Centered
      title="Pair this phone with a kitchen station"
      body="On the kitchen display, log in as the owner → Stations → Pair phone, then scan the QR code with this phone's camera."
      error={pairing.isError ? errorMessage(pairing.error) : null}
    />
  );
}

function Centered({ title, body, error }: { title: string; body?: string; error?: string | null }) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-4 p-8 text-center">
      <p className="text-2xl font-bold">{title}</p>
      {body && <p className="max-w-md text-lg text-stone-400">{body}</p>}
      {error && <p className="max-w-md rounded-xl bg-red-950 px-4 py-3 text-red-200">{error}</p>}
    </div>
  );
}
