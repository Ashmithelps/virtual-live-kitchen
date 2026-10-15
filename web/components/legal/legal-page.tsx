import Link from "next/link";
import type { ReactNode } from "react";
import { LEGAL } from "@/lib/legal";

/** Shared frame for /privacy, /terms and /recording-consent. */
export function LegalPage({ title, children }: { title: string; children: ReactNode }) {
  return (
    <article className="space-y-4 text-sm leading-relaxed text-stone-800 [&_h2]:mt-6 [&_h2]:text-base [&_h2]:font-semibold [&_li]:ml-5 [&_li]:list-disc [&_ul]:space-y-1">
      {!LEGAL.reviewed && (
        <p role="note" className="rounded-xl border border-amber-300 bg-amber-50 p-3 text-amber-900">
          Draft for legal review. This text has not been approved by a lawyer yet and may change before launch.
        </p>
      )}
      <h1 className="text-2xl font-bold">{title}</h1>
      <p className="text-stone-500">Effective: {LEGAL.effectiveDate ?? <Blank what="effective date" />}</p>
      {children}
      <p className="pt-4 text-stone-500">
        See also: <Link href="/privacy" className="underline">Privacy policy</Link> · <Link href="/terms" className="underline">Terms of use</Link> ·{" "}
        <Link href="/recording-consent" className="underline">Chef recording consent</Link>
      </p>
    </article>
  );
}

/** A value that must be configured before launch. */
export function Blank({ what }: { what: string }) {
  return <mark className="rounded bg-amber-100 px-1 text-amber-900">[{what}]</mark>;
}

export function Value({ value, what }: { value: string | undefined; what: string }) {
  return value ? <>{value}</> : <Blank what={what} />;
}

export function GrievanceOfficer() {
  return (
    <ul>
      <li>
        Name: <Value value={LEGAL.grievanceName} what="grievance officer name" />
      </li>
      <li>
        Email: <Value value={LEGAL.grievanceEmail} what="grievance email" />
      </li>
      <li>
        Phone: <Value value={LEGAL.grievancePhone} what="grievance phone" />
      </li>
      <li>
        Address: <Value value={LEGAL.address} what="registered address" />
      </li>
    </ul>
  );
}
