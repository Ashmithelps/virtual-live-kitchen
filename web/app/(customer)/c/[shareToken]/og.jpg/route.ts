import { loadPublicClip } from "@/lib/api/server";

/**
 * The clip's thumbnail at a stable URL for link previews (og:image). Storage links expire after
 * 10 minutes, but WhatsApp / Instagram fetch and cache the preview image whenever they like.
 */
export async function GET(_request: Request, { params }: { params: Promise<{ shareToken: string }> }) {
  const { shareToken } = await params;
  const clip = await loadPublicClip(shareToken);
  if (!clip.ok) return new Response("Not found", { status: clip.status === 410 ? 410 : 404 });
  const image = await fetch(clip.data.thumbnailUrl, { cache: "no-store" }).catch(() => null);
  if (!image?.ok || !image.body) return new Response("Thumbnail unavailable", { status: 502 });
  return new Response(image.body, {
    headers: {
      "Content-Type": "image/jpeg",
      // Short shared cache: a revoked link stops serving the image soon after.
      "Cache-Control": "public, max-age=600",
      "X-Robots-Tag": "noindex",
    },
  });
}
