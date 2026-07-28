import { isAccountLoggedIn, isLooseLoggedIn } from '@/utils/auth';
import { userAccount, userPlaylist } from '@/api/user';

export default {
  showToast({ state, commit }, text) {
    if (state.toast.timer !== null) {
      clearTimeout(state.toast.timer);
      commit('updateToast', { show: false, text: '', timer: null });
    }
    commit('updateToast', {
      show: true,
      text,
      timer: setTimeout(() => {
        commit('updateToast', {
          show: false,
          text: state.toast.text,
          timer: null,
        });
      }, 3200),
    });
  },
  fetchLikedPlaylist: ({ state, commit }) => {
    if (!isLooseLoggedIn() || !isAccountLoggedIn()) return Promise.resolve();
    return userPlaylist({
      uid: state.data.user?.userId,
      limit: 2000,
      timestamp: Date.now(),
    }).then(result => {
      if (!result.playlist) return;
      commit('updateLikedXXX', {
        name: 'playlists',
        data: result.playlist,
      });
      if (result.playlist.length) {
        commit('updateData', {
          key: 'likedSongPlaylistID',
          value: result.playlist[0].id,
        });
      }
    });
  },
  fetchUserProfile: ({ commit }) => {
    if (!isAccountLoggedIn()) return Promise.resolve();
    return userAccount().then(result => {
      if (result.code === 200) {
        commit('updateData', { key: 'user', value: result.profile });
      }
    });
  },
};
