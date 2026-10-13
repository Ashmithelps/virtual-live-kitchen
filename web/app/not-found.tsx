import Link from "next/link";

export default function NotFound() {
  return (
    <main className="mx-auto max-w-md space-y-3 px-4 py-24 text-center">
      <p className="text-5xl font-bold text-stone-300">404</p>
      <h1 className="text-xl font-semibold">Page not found</h1>
      <p className="text-stone-600">The link may be old, or the page has moved.</p>
      <Link href="/" className="inline-block font-medium text-brand-700 underline">
        Back to restaurants
      </Link>
    </main>
  );
}
