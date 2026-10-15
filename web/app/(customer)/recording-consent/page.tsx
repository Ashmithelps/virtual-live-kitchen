import type { Metadata } from "next";
import { LegalPage } from "@/components/legal/legal-page";
import { ErrorState } from "@/components/ui";
import { loadRecordingConsent } from "@/lib/api/server";

export const metadata: Metadata = { title: "Chef recording consent · Virtual Live Kitchen" };
// The text comes from the backend (the version chefs accept); refreshed hourly.
export const revalidate = 3600;

export default async function RecordingConsentPage() {
  const consent = await loadRecordingConsent();
  return (
    <LegalPage title="Chef recording consent">
      <p>
        Every chef must accept this text before a station camera may record them. Chefs see it in the kitchen app and can withdraw consent at any
        time in their settings; recording of that chef stops at once. A new version must be accepted again.
      </p>
      {consent.ok ? (
        <>
          <p className="text-stone-500">
            Version {consent.data.version} · fingerprint <code className="text-xs">{consent.data.textHash.slice(0, 16)}</code>
          </p>
          <pre className="whitespace-pre-wrap rounded-xl border border-stone-200 bg-white p-4 font-sans">{consent.data.text}</pre>
        </>
      ) : (
        <ErrorState message={consent.message} />
      )}
    </LegalPage>
  );
}
