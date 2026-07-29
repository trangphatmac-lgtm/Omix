import request from '@/utils/request';
import { mapTrackPlayableStatus } from '@/utils/common';

export function recommendPlaylist(params) {
  return request({ url: '/personalized', method: 'get', params });
}

export function dailyRecommendPlaylist(params) {
  return request({
    url: '/recommend/resource',
    method: 'get',
    params: { ...params, timestamp: Date.now() },
  });
}

export function getPlaylistDetail(id, noCache = false) {
  const params = { id };
  if (noCache) params.timestamp = Date.now();
  return request({
    url: '/playlist/detail',
    method: 'get',
    params,
  }).then(data => {
    if (data.playlist) {
      data.playlist.tracks = mapTrackPlayableStatus(
        data.playlist.tracks,
        data.privileges || []
      );
    }
    return data;
  });
}

export function highQualityPlaylist(params) {
  return request({
    url: '/top/playlist/highquality',
    method: 'get',
    params,
  });
}

export function topPlaylist(params) {
  return request({ url: '/top/playlist', method: 'get', params });
}

export function toplists() {
  return request({ url: '/toplist', method: 'get' });
}
