"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { kitchenApi } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import { KdsMessage } from "./kds-message";

/** Chefs must accept the current recording-consent text before they can be put on a station. */
export function ConsentGate({ onAccepted, onLogout }: { onAccepted: () => void; onLogout: () => void }) {
  const consent = useQuery({ queryKey: ["kds-consent"], queryFn: () => unwrap(kitchenApi.GET("/api/chef/consent")) });
  const accept = useMutation({
    mutationFn: () =>
      unwrap(kitchenApi.POST("/api/chef/consent", { body: { consentVersion: consent.data!.version, consentTextHash: consent.data!.textHash } })),
    onSuccess: onAccepted,
  });

  if (consent.isPending) return <KdsMessage title="Loading…" />;
  if (consent.isError) return <KdsMessage title="Couldn't load the consent text" body={errorMessage(consent.error)} action={{ label: "Try again", onClick: () => consent.refetch() }} />;

  return (
    <div className="mx-auto flex min-h-dvh max-w-3xl flex-col gap-4 p-6">
      <h1 className="text-2xl font-bold">Before your first shift</h1>
      <p className="text-stone-300">Cameras at kitchen stations record orders being prepared. Please read and accept this to be assigned to a station.</p>
      <pre className="flex-1 overflow-auto whitespace-pre-wrap rounded-2xl bg-stone-900 p-5 font-sans text-base leading-relaxed text-stone-200">{consent.data.text}</pre>
      {accept.isError && <p className="rounded-xl bg-red-950 px-4 py-3 text-red-200">{errorMessage(accept.error)}</p>}
      <div className="grid grid-cols-2 gap-3">
        <button onClick={onLogout} className="min-h-14 rounded-2xl border border-stone-600 text-lg font-semibold">
          Not now (log out)
        </button>
        <button onClick={() => accept.mutate()} disabled={accept.isPending} className="min-h-14 rounded-2xl bg-brand-600 text-lg font-bold text-white disabled:opacity-60">
          {accept.isPending ? "Saving…" : `I agree (version ${consent.data.version})`}
        </button>
      </div>
    </div>
  );
}
