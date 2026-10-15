export type ShareOutcome = "shared" | "copied" | "dismissed" | "failed";

/** Touch-first devices (phones, tablets): where the share sheet is the natural way to share. */
function isMobile(): boolean {
  return typeof window !== "undefined" && typeof window.matchMedia === "function" && window.matchMedia("(pointer: coarse)").matches;
}

/**
 * On phones, opens the share sheet (Web Share API); elsewhere, or if that isn't possible, copies the link.
 * A dismissed share sheet is not an error.
 */
export async function shareOrCopy(data: { title: string; text?: string; url: string }): Promise<ShareOutcome> {
  if (isMobile() && typeof navigator.share === "function" && (!navigator.canShare || navigator.canShare(data))) {
    try {
      await navigator.share(data);
      return "shared";
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return "dismissed";
      // Fall through to copying (e.g. NotAllowedError without a user gesture).
    }
  }
  return (await copyText(data.url)) ? "copied" : "failed";
}

export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}
