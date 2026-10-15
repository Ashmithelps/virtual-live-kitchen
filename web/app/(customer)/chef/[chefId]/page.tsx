import type { Metadata } from "next";
import Link from "next/link";
import { loadPublicChef } from "@/lib/api/server";
import { FollowButton } from "@/components/tip/follow-button";
import { Card } from "@/components/ui";

type Props = { params: Promise<{ chefId: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { chefId } = await params;
  const chef = await loadPublicChef(chefId);
  return chef.ok
    ? { title: `${chef.data.displayName} · ${chef.data.restaurantName}`, description: chef.data.specialty ?? `Chef at ${chef.data.restaurantName}` }
    : { title: "Chef · Virtual Live Kitchen" };
}

const count = new Intl.NumberFormat("en-IN");

/** Public chef profile (linked from shared clips): who they are, their clips' reach, follow. */
export default async function ChefPage({ params }: Props) {
  const { chefId } = await params;
  const chef = await loadPublicChef(chefId);
  if (!chef.ok) {
    return (
      <div className="space-y-3 py-16 text-center">
        <h1 className="text-xl font-semibold">{chef.status === 404 ? "Chef not found" : "Couldn't load this chef"}</h1>
        <p className="text-stone-600">{chef.status === 404 ? "They may have moved on from this kitchen." : chef.message}</p>
        <Link href="/" className="inline-block font-medium text-brand-700 underline">
          Find a restaurant
        </Link>
      </div>
    );
  }
  const c = chef.data;
  const stats = [
    { label: c.clipCount === 1 ? "clip" : "clips", value: c.clipCount },
    { label: c.totalViews === 1 ? "view" : "views", value: c.totalViews },
    { label: c.tipCount === 1 ? "tip" : "tips", value: c.tipCount },
    { label: c.followerCount === 1 ? "follower" : "followers", value: c.followerCount },
  ];
  return (
    <div className="space-y-4">
      <Card className="flex flex-col items-center gap-3 text-center">
        {c.photoUrl ? (
          <img src={c.photoUrl} alt={c.displayName} className="size-28 rounded-full object-cover" />
        ) : (
          <div className="flex size-28 items-center justify-center rounded-full bg-stone-100 text-5xl" aria-hidden>
            👩‍🍳
          </div>
        )}
        <div>
          <h1 className="text-2xl font-bold">{c.displayName}</h1>
          {c.specialty && <p className="text-stone-600">{c.specialty}</p>}
          <Link href={`/r/${c.restaurantSlug}`} className="text-sm text-brand-700 underline">
            {c.restaurantName}, {c.restaurantCity}
          </Link>
        </div>
        {c.hygiene && (
          <span className="rounded-full bg-green-50 px-3 py-1 text-xs font-medium text-green-800"
            title={`Average AI hygiene score of ${c.hygiene.clipCount} of their kitchen clips in the last ${c.hygiene.windowDays} days`}>
            🧤 Hygiene {Math.round(c.hygiene.score)}/100 · {c.hygiene.clipCount} clips
          </span>
        )}
        <dl className="grid w-full max-w-sm grid-cols-4 gap-2">
          {stats.map((s) => (
            <div key={s.label}>
              <dd className="text-xl font-bold tabular-nums">{count.format(s.value)}</dd>
              <dt className="text-xs text-stone-500">{s.label}</dt>
            </div>
          ))}
        </dl>
        {c.bio && <p className="max-w-md text-stone-700">{c.bio}</p>}
        <FollowButton chefId={c.id} chefName={c.displayName} tippable={c.tippable} />
      </Card>
      <Link href={`/r/${c.restaurantSlug}`} className="block rounded-2xl bg-brand-600 px-5 py-4 text-center text-lg font-semibold text-white hover:bg-brand-700">
        Order from {c.restaurantName}
      </Link>
    </div>
  );
}
