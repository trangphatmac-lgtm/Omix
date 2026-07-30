import Cookies from 'js-cookie';
import { logout } from '@/api/auth';
import store from '@/store';
import { flushMusicStorage } from '@/omix/storageBridge';

export async function setCookies(string) {
  const cookies = (Array.isArray(string) ? string : string.split(';;'))
    .map(cookie => cookie.trim())
    .filter(Boolean);
  cookies.forEach(cookie => {
    document.cookie = cookie;
    const first = cookie.split(';', 1)[0];
    const split = first.indexOf('=');
    if (split < 1) return;
    const name = first.slice(0, split).trim();
    if (!['MUSIC_U', '__csrf'].includes(name)) return;
    localStorage.setItem(`cookie-${name}`, first.slice(split + 1).trim());
  });
  await flushMusicStorage();
}

export function getCookie(key) {
  return Cookies.get(key) ?? localStorage.getItem(`cookie-${key}`);
}

export function removeCookie(key) {
  Cookies.remove(key);
  localStorage.removeItem(`cookie-${key}`);
}

// MUSIC_U 只有在账户登录的情况下才有
export function isLoggedIn() {
  return getCookie('MUSIC_U') !== undefined;
}

// 账号登录
export function isAccountLoggedIn() {
  return (
    getCookie('MUSIC_U') !== undefined &&
    store.state.data.loginMode === 'account'
  );
}

// 用户名搜索（用户数据为只读）
export function isUsernameLoggedIn() {
  return store.state.data.loginMode === 'username';
}

// 账户登录或者用户名搜索都判断为登录，宽松检查
export function isLooseLoggedIn() {
  return isAccountLoggedIn() || isUsernameLoggedIn();
}

export function doLogout() {
  logout();
  removeCookie('MUSIC_U');
  removeCookie('__csrf');
  // 更新状态仓库中的用户信息
  store.commit('updateData', { key: 'user', value: {} });
  // 更新状态仓库中的登录状态
  store.commit('updateData', { key: 'loginMode', value: null });
  // 更新状态仓库中的喜欢列表
  store.commit('updateData', { key: 'likedSongPlaylistID', value: undefined });
  void flushMusicStorage();
}
