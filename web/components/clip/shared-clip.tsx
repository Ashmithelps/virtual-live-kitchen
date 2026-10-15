"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef } from "react";
import { api } from "@/lib/api/client";
import type { PublicClip } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { visitorId } from "@/lib/visitor";
import { Card } from "@/components/ui";

/** The shared clip: player, restaurant and chef, an order button, and one view per visitor per day. */
export function SharedClip({ token, clip }: { token: string; clip: PublicClip }) {
  const router = useRouter();
  const counted = useRef(false);

  useEffect(() => {
    if (counted.current) return;
    counted.current = true;
    void api.POST("/api/public/clips/{token}/views", { params: { path: { token } }, body: { visitorId: visitorId() } }).catch(() => undefined);
  }, [token]);

  return (
    <div className="space-y-4">
      <Card className="overflow-hidden p-0!">
        <video
          src={clip.videoUrl}
          poster={clip.thumbnailUrl}
          controls
          playsInline
          preload="metadata"
          className="aspect-video w-full bg-black"
          // Watch links last 10 minutes; a page left open longer reloads for fresh ones.
          onError={() => router.refresh()}
        />
        <div className="space-y-1 p-4">
          <h1 className="text-lg font-semibold">{clip.title}</h1>
          <p className="text-sm text-stone-600">
            {clip.restaurantName}, {clip.restaurantCity} · {Math.round(clip.durationSeconds)} s · {formatDateTime(clip.recordedAt)}
          </p>
          {clip.chefName && (
            <p className="text-sm">
              Cooked by <span className="font-medium">{clip.chefName}</span>
              {clip.chefId && (
                <>
                  {" · "}
                  <Link href={`/chef/${clip.chefId}`} className="font-medium text-brand-700 underline">
                    View chef
                  </Link>
                </>
              )}
            </p>
          )}
        </div>
      </Card>
      {clip.restaurantOrderable ? (
        <Link href={`/r/${clip.restaurantSlug}`} className="block rounded-2xl bg-brand-600 px-5 py-4 text-center text-lg font-semibold text-white hover:bg-brand-700">
          Order from {clip.restaurantName}
        </Link>
      ) : (
        <p className="text-center text-sm text-stone-500">{clip.restaurantName} isn&apos;t taking orders here right now.</p>
      )}
      <p className="text-center text-xs text-stone-500">Every order on Virtual Live Kitchen comes with a clip of it being cooked.</p>
    </div>
  );
}
