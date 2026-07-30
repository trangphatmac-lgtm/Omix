const STORAGE_KEYS = [
  'appVersion',
  'settings',
  'data',
  'player',
  'playerCurrentTrackTime',
  'cookie-MUSIC_U',
  'cookie-__csrf',
];

let lastSnapshot = '';
let flushTimer;

function snapshot() {
  const values = {};
  for (const key of STORAGE_KEYS) {
    const value = window.localStorage.getItem(key);
    if (value !== null) values[key] = value;
  }
  return values;
}

function restoreCookies() {
  for (const name of ['MUSIC_U', '__csrf']) {
    const value = window.localStorage.getItem(`cookie-${name}`);
    if (value) {
      document.cookie = `${name}=${value}; path=/; SameSite=Lax`;
    }
  }
}

export async function hydrateMusicStorage() {
  try {
    const response = await fetch('/api/v1/music/storage', {
      credentials: 'same-origin',
    });
    if (!response.ok) return;
    const values = await response.json();
    for (const key of STORAGE_KEYS) {
      if (typeof values[key] === 'string') {
        window.localStorage.setItem(key, values[key]);
      }
    }
    restoreCookies();
    lastSnapshot = JSON.stringify(snapshot());
  } catch (error) {
    console.warn('Music storage is unavailable; using session storage', error);
  }
}

export async function flushMusicStorage() {
  try {
    const values = snapshot();
    const serialized = JSON.stringify(values);
    if (serialized === lastSnapshot) return;
    const response = await fetch('/api/v1/music/storage', {
      method: 'PUT',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json'},
      body: serialized,
    });
    if (response.ok) lastSnapshot = serialized;
  } catch (error) {
    console.warn('Music storage write failed', error);
  }
}

export function startMusicStorageSync() {
  window.setInterval(() => void flushMusicStorage(), 1500);
  window.addEventListener('pagehide', () => void flushMusicStorage());
  window.addEventListener('visibilitychange', () => {
    document.documentElement.classList.toggle(
      'omix-music-hidden',
      document.visibilityState === 'hidden'
    );
    if (document.visibilityState === 'hidden') void flushMusicStorage();
  });
  window.addEventListener('storage', () => {
    window.clearTimeout(flushTimer);
    flushTimer = window.setTimeout(() => void flushMusicStorage(), 100);
  });
}

export async function acknowledgeMusicScreen() {
  for (let attempt = 0; attempt < 40; attempt++) {
    try {
      const response = await fetch('/api/v1/client/virtualScreen', {
        method: 'POST',
        credentials: 'same-origin',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({name: 'music'}),
      });
      if (response.ok) return;
    } catch {
      // JCEF can paint before the local bridge is ready.
    }
    await new Promise(resolve => window.setTimeout(resolve, 250));
  }
}
