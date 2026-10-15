import type { Metadata } from "next";
import Link from "next/link";
import { loadPublicClip } from "@/lib/api/server";
import { SharedClip } from "@/components/clip/shared-clip";

type Props = { params: Promise<{ shareToken: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { shareToken } = await params;
  const clip = await loadPublicClip(shareToken);
  if (!clip.ok) return { title: "Kitchen clip · Virtual Live Kitchen", robots: { index: false } };
  const { title, restaurantName, restaurantCity, chefName } = clip.data;
  const description = `${chefName ? `${chefName} cooking` : "Cooked"} at ${restaurantName}, ${restaurantCity}. Watch the kitchen clip on Virtual Live Kitchen.`;
  const image = { url: `/c/${shareToken}/og.jpg`, width: 640, height: 360, alt: `Kitchen clip from ${restaurantName}` };
  return {
    title,
    description,
    // Shared clips are personal: previews yes, search engines no.
    robots: { index: false, follow: false },
    openGraph: { type: "video.other", title, description, url: `/c/${shareToken}`, siteName: "Virtual Live Kitchen", images: [image] },
    twitter: { card: "summary_large_image", title, description, images: [image] },
  };
}

/** Public page for a shared clip (/c/{token}); anyone with the link can watch. */
export default async function SharedClipPage({ params }: Props) {
  const { shareToken } = await params;
  const clip = await loadPublicClip(shareToken);
  if (!clip.ok) {
    const gone = clip.status === 410;
    return (
      <div className="space-y-3 py-16 text-center">
        <p className="text-4xl" aria-hidden>
          🎬
        </p>
        <h1 className="text-xl font-semibold">{gone ? "This clip has expired" : clip.status === 404 ? "This link doesn't work anymore" : "Couldn't load this clip"}</h1>
        <p className="text-stone-600">
          {gone ? "Kitchen clips are kept for a limited time." : clip.status === 404 ? "Whoever shared it may have turned it off." : clip.message}
        </p>
        <Link href="/" className="inline-block font-medium text-brand-700 underline">
          Find a restaurant
        </Link>
      </div>
    );
  }
  return <SharedClip token={shareToken} clip={clip.data} />;
}
