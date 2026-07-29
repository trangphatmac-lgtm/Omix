import { isAccountLoggedIn, isLooseLoggedIn } from '@/utils/auth';
import { likeATrack } from '@/api/track';
import { getPlaylistDetail } from '@/api/playlist';
import { getTrackDetail } from '@/api/track';
import {
  userAccount,
  userPlaylist,
  userPlayHistory,
  userLikedSongsIDs,
  likedAlbums,
  likedArtists,
  likedMVs,
  cloudDisk,
} from '@/api/user';

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
  likeATrack({ state, commit, dispatch }, id) {
    if (!isAccountLoggedIn()) {
      dispatch('showToast', '此操作需要登录网易云账号');
      return Promise.resolve();
    }
    const like = !state.liked.songs.includes(id);
    return likeATrack({ id, like }).then(() => {
      commit('updateLikedXXX', {
        name: 'songs',
        data: like
          ? [...state.liked.songs, id]
          : state.liked.songs.filter(trackID => trackID !== id),
      });
      return dispatch('fetchLikedSongsWithDetails');
    });
  },
  fetchLikedSongs: ({ state, commit }) => {
    if (!isAccountLoggedIn()) return Promise.resolve();
    return userLikedSongsIDs({ uid: state.data.user?.userId }).then(result => {
      if (result.ids) {
        commit('updateLikedXXX', { name: 'songs', data: result.ids });
      }
    });
  },
  fetchLikedSongsWithDetails: ({ state, commit }) => {
    const playlistID =
      state.data.likedSongPlaylistID || state.liked.playlists[0]?.id;
    if (!playlistID) return Promise.resolve();
    return getPlaylistDetail(playlistID, true).then(result => {
      const ids = result.playlist?.trackIds?.slice(0, 12).map(t => t.id) || [];
      if (!ids.length) {
        commit('updateLikedXXX', { name: 'songsWithDetails', data: [] });
        return;
      }
      return getTrackDetail(ids.join(',')).then(detail => {
        commit('updateLikedXXX', {
          name: 'songsWithDetails',
          data: detail.songs || [],
        });
      });
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
  fetchLikedAlbums: ({ commit }) => {
    if (!isAccountLoggedIn()) return Promise.resolve();
    return likedAlbums({ limit: 2000 }).then(result => {
      commit('updateLikedXXX', { name: 'albums', data: result.data || [] });
    });
  },
  fetchLikedArtists: ({ commit }) => {
    if (!isAccountLoggedIn()) return Promise.resolve();
    return likedArtists({ limit: 2000 }).then(result => {
      commit('updateLikedXXX', { name: 'artists', data: result.data || [] });
    });
  },
  fetchLikedMVs: ({ commit }) => {
    if (!isAccountLoggedIn()) return Promise.resolve();
    return likedMVs({ limit: 1000 }).then(result => {
      commit('updateLikedXXX', { name: 'mvs', data: result.data || [] });
    });
  },
  fetchCloudDisk: ({ commit }) => {
    if (!isAccountLoggedIn()) return Promise.resolve();
    return cloudDisk({ limit: 1000 }).then(result => {
      commit('updateLikedXXX', { name: 'cloudDisk', data: result.data || [] });
    });
  },
  fetchPlayHistory: ({ state, commit }) => {
    if (!isAccountLoggedIn()) return Promise.resolve();
    return Promise.all([
      userPlayHistory({ uid: state.data.user?.userId, type: 0 }),
      userPlayHistory({ uid: state.data.user?.userId, type: 1 }),
    ]).then(([allResult, weekResult]) => {
      const normalize = (result, key) =>
        (result?.[key] || []).map(item => ({
          ...item.song,
          playCount: item.playCount,
        }));
      commit('updateLikedXXX', {
        name: 'playHistory',
        data: {
          allData: normalize(allResult, 'allData'),
          weekData: normalize(weekResult, 'weekData'),
        },
      });
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
