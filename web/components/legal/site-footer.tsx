import Link from "next/link";

export function SiteFooter() {
  return (
    <footer className="mx-auto flex w-full max-w-6xl flex-wrap gap-x-4 gap-y-1 px-4 py-6 text-xs text-stone-500">
      <span className="mr-auto">© Virtual Live Kitchen</span>
      <Link href="/privacy" className="hover:text-stone-800">Privacy</Link>
      <Link href="/terms" className="hover:text-stone-800">Terms</Link>
      <Link href="/recording-consent" className="hover:text-stone-800">Chef recording consent</Link>
      <Link href="/privacy#grievance" className="hover:text-stone-800">Grievances</Link>
    </footer>
  );
}
