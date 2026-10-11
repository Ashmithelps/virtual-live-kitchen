import Link from "next/link";
import { formatPaise } from "@/lib/format";

/** Sticky bottom bar on phones; sits at the bottom of the content column on wider screens. */
export function CartBar({ count, subtotal, href }: { count: number; subtotal: number; href: string }) {
  if (count === 0) return null;
  return (
    <div className="pb-safe fixed inset-x-0 bottom-0 z-30 px-4 pt-3">
      <Link href={href} className="mx-auto flex max-w-2xl items-center justify-between rounded-2xl bg-brand-600 px-5 py-3.5 text-white shadow-lg hover:bg-brand-700">
        <span className="text-sm font-semibold">
          {count} item{count === 1 ? "" : "s"} · {formatPaise(subtotal)}
        </span>
        <span className="font-semibold">View cart →</span>
      </Link>
    </div>
  );
}
