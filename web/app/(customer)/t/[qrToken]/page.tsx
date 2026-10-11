"use client";

import { useQuery } from "@tanstack/react-query";
import { useParams, useRouter } from "next/navigation";
import { useEffect } from "react";
import { api } from "@/lib/api/client";
import { ApiError, errorMessage, unwrap } from "@/lib/api/errors";
import { saveTableSession } from "@/lib/table-session";
import { EmptyState, ErrorState, PageLoading } from "@/components/ui";

/**
 * Where a table's QR code points. Resolves the code, remembers the table session for this
 * restaurant, then opens the menu in dine-in mode (replace, so "back" doesn't re-scan).
 */
export default function TableLandingPage() {
  const { qrToken } = useParams<{ qrToken: string }>();
  const router = useRouter();
  const lookup = useQuery({
    queryKey: ["table", qrToken],
    queryFn: () => unwrap(api.GET("/api/tables/{qrToken}", { params: { path: { qrToken } } })),
    staleTime: 0,
    gcTime: 0,
  });

  useEffect(() => {
    if (lookup.data) {
      saveTableSession(lookup.data);
      router.replace(`/r/${lookup.data.restaurantSlug}`);
    }
  }, [lookup.data, router]);

  if (lookup.isError) {
    if (lookup.error instanceof ApiError && lookup.error.status === 404) {
      return <EmptyState title="This QR code isn't valid any more" message="It may have been replaced. Please ask the staff for the current code for your table." />;
    }
    return <ErrorState message={errorMessage(lookup.error)} onRetry={() => lookup.refetch()} />;
  }
  return <PageLoading label="Finding your table…" />;
}
