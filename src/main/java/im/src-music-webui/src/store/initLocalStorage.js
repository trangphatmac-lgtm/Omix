import { playlistCategories } from '@/utils/staticData';

console.debug('[debug][initLocalStorage.js]');
const enabledPlaylistCategories = playlistCategories
  .filter(c => c.enable)
  .map(c => c.name);

let localStorage = {
  player: {},
  settings: {
    lang: null,
    musicLanguage: 'all',
    appearance: 'auto',
    themeColor: 'default',
    musicQuality: 320000,
    lyricFontSize: 28,
    automaticallyCacheSongs: false,
    cacheLimit: 4096,
    showPlaylistsByAppleMusic: true,
    enableReversedMode: false,
    nyancatStyle: false,
    showLyricsTranslation: true,
    lyricsBackground: true,
    showLyricsTime: false,
    showLibraryDefault: false,
    subTitleDefault: false,
    enabledPlaylistCategories,
  },
  data: {
    user: {},
    likedSongPlaylistID: 0,
    lastRefreshCookieDate: 0,
    loginMode: null,
  },
};

export default localStorage;
