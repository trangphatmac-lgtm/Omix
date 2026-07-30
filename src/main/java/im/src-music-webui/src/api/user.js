import request from '@/utils/request';

export function userAccount() {
  return request({
    url: '/user/account',
    method: 'get',
    params: { timestamp: Date.now() },
  });
}

export function userPlaylist(params) {
  return request({ url: '/user/playlist', method: 'get', params });
}

export function userPlayHistory(params) {
  return request({ url: '/user/record', method: 'get', params });
}

export function userLikedSongsIDs(params) {
  return request({
    url: '/likelist',
    method: 'get',
    params: { ...params, timestamp: Date.now() },
  });
}

export function likedAlbums(params = {}) {
  return request({
    url: '/album/sublist',
    method: 'get',
    params: { ...params, timestamp: Date.now() },
  });
}

export function likedArtists(params = {}) {
  return request({
    url: '/artist/sublist',
    method: 'get',
    params: { ...params, timestamp: Date.now() },
  });
}

export function likedMVs(params = {}) {
  return request({
    url: '/mv/sublist',
    method: 'get',
    params: { ...params, timestamp: Date.now() },
  });
}

export function cloudDisk(params = {}) {
  return request({
    url: '/user/cloud',
    method: 'get',
    params: { ...params, timestamp: Date.now() },
  });
}

export function uploadSong(file) {
  const data = new FormData();
  data.append('songFile', file);
  return request({
    url: '/cloud',
    method: 'post',
    params: { timestamp: Date.now() },
    data,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 200000,
  });
}

export function cloudDiskTrackDelete(id) {
  return request({
    url: '/user/cloud/del',
    method: 'post',
    params: { id, timestamp: Date.now() },
  });
}
