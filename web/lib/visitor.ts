const KEY = "vk.visitor";

/**
 * A random id this browser keeps, so a shared clip counts one view per visitor per day. It identifies
 * nothing about the person; the backend stores only its hash. Falls back to a per-page id without storage.
 */
export function visitorId(): string {
  try {
    const existing = window.localStorage.getItem(KEY);
    if (existing) return existing;
    const id = crypto.randomUUID();
    window.localStorage.setItem(KEY, id);
    return id;
  } catch {
    return crypto.randomUUID();
  }
}
