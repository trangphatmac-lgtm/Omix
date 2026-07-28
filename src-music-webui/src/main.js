import {
  acknowledgeMusicScreen,
  hydrateMusicStorage,
  startMusicStorageSync,
} from '@/omix/storageBridge';

async function start() {
  await hydrateMusicStorage();
  startMusicStorageSync();
  await acknowledgeMusicScreen();
  await import('./app-entry');
}

start().catch(error => {
  console.error('Failed to start Omix Music', error);
  const target = document.querySelector('#app');
  if (target) {
    target.textContent = `Omix Music 启动失败：${error?.message || error}`;
    target.className = 'omix-music-startup-error';
  }
});
