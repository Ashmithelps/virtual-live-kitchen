// Service worker for the kitchen display PWA (registered with scope /kitchen, production only).
//
// Goal: the app shell opens even when the restaurant's Wi-Fi is flaky. Live data never comes
// from cache: API calls go to another origin and /api/* session calls are passed straight through.
const CACHE = "vk-kitchen-v1";
const SHELL = ["/kitchen", "/kitchen.webmanifest", "/icons/kitchen-192.png", "/icons/kitchen-512.png"];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches
      .open(CACHE)
      .then((cache) => cache.addAll(SHELL))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => key.startsWith("vk-kitchen-") && key !== CACHE).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== "GET" || url.origin !== self.location.origin || url.pathname.startsWith("/api/")) {
    return; // backend API, WebSocket, session handlers: always network
  }

  if (request.mode === "navigate") {
    // Network first so a redeploy shows up immediately; cached shell when offline.
    event.respondWith(
      fetch(request)
        .then((response) => {
          const copy = response.clone();
          caches.open(CACHE).then((cache) => cache.put("/kitchen", copy));
          return response;
        })
        .catch(() => caches.match("/kitchen")),
    );
    return;
  }

  if (url.pathname.startsWith("/_next/static/") || url.pathname.startsWith("/icons/")) {
    // Content-hashed build assets never change: cache first.
    event.respondWith(
      caches.match(request).then(
        (cached) =>
          cached ||
          fetch(request).then((response) => {
            if (response.ok) {
              const copy = response.clone();
              caches.open(CACHE).then((cache) => cache.put(request, copy));
            }
            return response;
          }),
      ),
    );
  }
});
