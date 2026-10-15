import { afterEach, describe, expect, it, vi } from "vitest";
import { shareOrCopy } from "./share";

const data = { title: "Watch my order", url: "https://example.test/c/abc" };

function stubNavigator(nav: Partial<Navigator>, mobile = true) {
  vi.stubGlobal("navigator", nav);
  vi.stubGlobal("matchMedia", (query: string) => ({ matches: mobile && query === "(pointer: coarse)" }));
}

describe("shareOrCopy", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("uses the share sheet when the browser has one", async () => {
    const share = vi.fn().mockResolvedValue(undefined);
    const writeText = vi.fn();
    stubNavigator({ share, canShare: () => true, clipboard: { writeText } as unknown as Clipboard });
    expect(await shareOrCopy(data)).toBe("shared");
    expect(share).toHaveBeenCalledWith(data);
    expect(writeText).not.toHaveBeenCalled();
  });

  it("treats closing the share sheet as dismissed, not as a failure", async () => {
    stubNavigator({ share: vi.fn().mockRejectedValue(new DOMException("cancelled", "AbortError")) });
    expect(await shareOrCopy(data)).toBe("dismissed");
  });

  it("copies on desktop even when the browser has a share sheet", async () => {
    const share = vi.fn();
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubNavigator({ share, clipboard: { writeText } as unknown as Clipboard }, false);
    expect(await shareOrCopy(data)).toBe("copied");
    expect(share).not.toHaveBeenCalled();
  });

  it("copies the link when there is no share sheet", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubNavigator({ clipboard: { writeText } as unknown as Clipboard });
    expect(await shareOrCopy(data)).toBe("copied");
    expect(writeText).toHaveBeenCalledWith(data.url);
  });

  it("falls back to copying when sharing is refused, and reports when even that fails", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubNavigator({ share: vi.fn().mockRejectedValue(new DOMException("no", "NotAllowedError")), clipboard: { writeText } as unknown as Clipboard });
    expect(await shareOrCopy(data)).toBe("copied");
    stubNavigator({ clipboard: { writeText: vi.fn().mockRejectedValue(new Error("denied")) } as unknown as Clipboard });
    expect(await shareOrCopy(data)).toBe("failed");
  });
});
