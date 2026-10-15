import { api } from "@/lib/api/client";
import { unwrap } from "@/lib/api/errors";
import type { UploadPurpose } from "@/lib/api/types";

export const IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

/** Image check before asking for an upload URL; returns a message, or null when the file is fine. */
export function imageProblem(file: File, allowPdf = false): string | null {
  if (allowPdf && file.type === "application/pdf") return file.size > MAX_IMAGE_BYTES ? "Files must be 5 MB or smaller." : null;
  if (!IMAGE_TYPES.includes(file.type)) return allowPdf ? "Use a PDF, JPG, PNG or WebP file." : "Use a JPG, PNG or WebP image.";
  if (file.size > MAX_IMAGE_BYTES) return "Images must be 5 MB or smaller.";
  return null;
}

/**
 * Uploads an image straight to storage through a presigned PUT and returns its object key, which
 * the restaurant / menu item / chef form then saves (the backend stores keys, never URLs).
 */
export async function uploadImage(restaurantId: string, purpose: UploadPurpose, file: File): Promise<string> {
  const problem = imageProblem(file, purpose === "FSSAI_LICENCE");
  if (problem) throw new Error(problem);
  const upload = await unwrap(
    api.POST("/api/owner/restaurants/{restaurantId}/uploads", { params: { path: { restaurantId } }, body: { purpose, contentType: file.type } }),
  );
  const response = await fetch(upload.uploadUrl, { method: upload.method, headers: upload.headers as Record<string, string>, body: file });
  if (!response.ok) throw new Error(`Upload failed (${response.status})`);
  return upload.objectKey;
}
