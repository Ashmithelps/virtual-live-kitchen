"use client";

import type { ChunkMeta, PendingUpload } from "./buffer";

/**
 * IndexedDB for the station phone. Chunk metadata and chunk bytes live in separate stores so
 * listing the buffer never loads ~45 minutes of video into memory.
 */
const DB_NAME = "vk-station";
const VERSION = 1;
const META = "chunkMeta";
const DATA = "chunkData";
const UPLOADS = "uploads";

let dbPromise: Promise<IDBDatabase> | null = null;

function open(): Promise<IDBDatabase> {
  dbPromise ??= new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      db.createObjectStore(META, { keyPath: "id" });
      db.createObjectStore(DATA);
      db.createObjectStore(UPLOADS, { keyPath: "orderId" });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
  return dbPromise;
}

function done(tx: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error ?? new Error("IndexedDB transaction aborted"));
  });
}

function result<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export async function saveChunk(meta: ChunkMeta, blob: Blob): Promise<void> {
  const db = await open();
  const tx = db.transaction([META, DATA], "readwrite");
  tx.objectStore(DATA).put(blob, meta.id);
  tx.objectStore(META).put(meta);
  await done(tx);
}

export async function listChunks(): Promise<ChunkMeta[]> {
  const db = await open();
  return result(db.transaction(META).objectStore(META).getAll() as IDBRequest<ChunkMeta[]>);
}

export async function chunkBlob(id: string): Promise<Blob | undefined> {
  const db = await open();
  return result(db.transaction(DATA).objectStore(DATA).get(id) as IDBRequest<Blob | undefined>);
}

export async function deleteChunks(ids: string[]): Promise<void> {
  if (ids.length === 0) return;
  const db = await open();
  const tx = db.transaction([META, DATA], "readwrite");
  for (const id of ids) {
    tx.objectStore(META).delete(id);
    tx.objectStore(DATA).delete(id);
  }
  await done(tx);
}

export async function listUploads(): Promise<PendingUpload[]> {
  const db = await open();
  return result(db.transaction(UPLOADS).objectStore(UPLOADS).getAll() as IDBRequest<PendingUpload[]>);
}

export async function saveUpload(upload: PendingUpload): Promise<void> {
  const db = await open();
  const tx = db.transaction(UPLOADS, "readwrite");
  tx.objectStore(UPLOADS).put(upload);
  await done(tx);
}

/** Adds the window unless it's already queued (pushes and catch-up can overlap). */
export async function enqueueUpload(upload: PendingUpload): Promise<boolean> {
  const db = await open();
  const tx = db.transaction(UPLOADS, "readwrite");
  const store = tx.objectStore(UPLOADS);
  const existing = await result(store.get(upload.orderId));
  if (!existing) store.put(upload);
  await done(tx);
  return !existing;
}

export async function removeUpload(orderId: string): Promise<void> {
  const db = await open();
  const tx = db.transaction(UPLOADS, "readwrite");
  tx.objectStore(UPLOADS).delete(orderId);
  await done(tx);
}
