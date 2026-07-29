const CACHE_NAME = 'omix-music-audio-v1';
const CACHE_PREFIX = '/__omix_audio_cache__/';

function cacheRequest(trackID, quality) {
  return new Request(
    `${window.location.origin}${CACHE_PREFIX}${quality}/${trackID}`
  );
}

function contentLength(response) {
  return Number(response.headers.get('content-length')) || 0;
}

export async function getCachedAudioSource(trackID, quality) {
  if (!('caches' in window)) return null;
  const cache = await caches.open(CACHE_NAME);
  const response = await cache.match(cacheRequest(trackID, quality));
  if (!response) return null;
  return URL.createObjectURL(await response.blob());
}

export async function cacheAudioSource(url, trackID, quality, limitInMB) {
  if (!('caches' in window) || !url) return;
  const cache = await caches.open(CACHE_NAME);
  const key = cacheRequest(trackID, quality);
  if (await cache.match(key)) return;

  const response = await fetch(url, { credentials: 'omit' });
  if (!response.ok) return;
  await cache.put(key, response);
  await trimAudioCache(limitInMB);
}

export async function getAudioCacheStats() {
  if (!('caches' in window)) return { length: 0, bytes: 0 };
  const cache = await caches.open(CACHE_NAME);
  const keys = await cache.keys();
  let bytes = 0;
  for (const key of keys) {
    const response = await cache.match(key);
    bytes += contentLength(response);
  }
  return { length: keys.length, bytes };
}

export async function clearAudioCache() {
  if (!('caches' in window)) return;
  await caches.delete(CACHE_NAME);
}

async function trimAudioCache(limitInMB) {
  const limit = Number(limitInMB);
  if (!limit || !('caches' in window)) return;
  const cache = await caches.open(CACHE_NAME);
  const keys = await cache.keys();
  let total = 0;
  const entries = [];

  for (const key of keys) {
    const response = await cache.match(key);
    const size = contentLength(response);
    total += size;
    entries.push({ key, size });
  }

  const maxBytes = limit * 1024 * 1024;
  for (const entry of entries) {
    if (total <= maxBytes) break;
    await cache.delete(entry.key);
    total -= entry.size;
  }
}
