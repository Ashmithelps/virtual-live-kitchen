import Link from "next/link";
import type { ButtonHTMLAttributes, ReactNode } from "react";

type Variant = "primary" | "secondary" | "ghost" | "danger";

const VARIANT: Record<Variant, string> = {
  primary: "bg-brand-600 text-white hover:bg-brand-700 disabled:bg-stone-300",
  secondary: "border border-stone-300 bg-white text-stone-800 hover:bg-stone-100 disabled:text-stone-400",
  ghost: "text-brand-700 hover:bg-brand-50 disabled:text-stone-400",
  danger: "border border-red-300 bg-white text-red-700 hover:bg-red-50 disabled:text-stone-400",
};

export function Button({
  variant = "primary",
  className = "",
  busy = false,
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; busy?: boolean }) {
  return (
    <button
      {...props}
      disabled={props.disabled || busy}
      className={`inline-flex min-h-11 items-center justify-center gap-2 rounded-xl px-4 py-2 text-sm font-semibold transition disabled:cursor-not-allowed ${VARIANT[variant]} ${className}`}
    >
      {busy && <Spinner small />}
      {children}
    </button>
  );
}

export function Spinner({ small = false }: { small?: boolean }) {
  return (
    <span
      role="status"
      aria-label="Loading"
      className={`inline-block animate-spin rounded-full border-2 border-current border-r-transparent ${small ? "size-4" : "size-8 text-brand-600"}`}
    />
  );
}

export function PageLoading({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="flex flex-col items-center gap-3 py-16 text-stone-500">
      <Spinner />
      <p className="text-sm">{label}</p>
    </div>
  );
}

export function ErrorState({ title = "Something went wrong", message, onRetry }: { title?: string; message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="rounded-2xl border border-red-200 bg-red-50 p-5 text-center">
      <p className="font-semibold text-red-800">{title}</p>
      <p className="mt-1 text-sm text-red-700">{message}</p>
      {onRetry && (
        <Button variant="secondary" className="mt-4" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}

export function EmptyState({ title, message, action }: { title: string; message?: string; action?: ReactNode }) {
  return (
    <div className="rounded-2xl border border-dashed border-stone-300 bg-white p-8 text-center">
      <p className="font-semibold text-stone-800">{title}</p>
      {message && <p className="mt-1 text-sm text-stone-500">{message}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function InlineError({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
      {message}
    </p>
  );
}

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <section className={`rounded-2xl border border-stone-200 bg-white p-4 ${className}`}>{children}</section>;
}

/** Indian veg/non-veg mark: green square-dot for veg, red for non-veg. */
export function VegMark({ veg }: { veg: boolean }) {
  return (
    <span
      title={veg ? "Vegetarian" : "Non-vegetarian"}
      aria-label={veg ? "Vegetarian" : "Non-vegetarian"}
      className={`inline-flex size-4 shrink-0 items-center justify-center rounded-sm border-2 ${veg ? "border-green-700" : "border-red-700"}`}
    >
      <span className={`size-1.5 rounded-full ${veg ? "bg-green-700" : "bg-red-700"}`} />
    </span>
  );
}

export function Badge({ children, tone = "stone" }: { children: ReactNode; tone?: "stone" | "green" | "red" | "amber" | "brand" }) {
  const tones = {
    stone: "bg-stone-100 text-stone-700",
    green: "bg-green-100 text-green-800",
    red: "bg-red-100 text-red-800",
    amber: "bg-amber-100 text-amber-800",
    brand: "bg-brand-100 text-brand-700",
  };
  return <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${tones[tone]}`}>{children}</span>;
}

export function TextLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <Link href={href} className="font-semibold text-brand-700 underline-offset-2 hover:underline">
      {children}
    </Link>
  );
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="text-sm font-medium text-stone-700">{label}</span>
      <div className="mt-1">{children}</div>
      {hint && <span className="mt-1 block text-xs text-stone-500">{hint}</span>}
    </label>
  );
}

export const inputClass =
  "w-full rounded-xl border border-stone-300 bg-white px-3 py-2.5 text-base outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100";
