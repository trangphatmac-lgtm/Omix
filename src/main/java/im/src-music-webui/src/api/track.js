import request from '@/utils/request';
import { mapTrackPlayableStatus } from '@/utils/common';
import store from '@/store';

export function getMP3(id) {
  const quality = store.state.settings?.musicQuality ?? 320000;
  return request({
    url: '/song/url',
    method: 'get',
    params: { id, br: quality === 'flac' ? 350000 : quality },
  });
}

export function getTrackDetail(ids) {
  return request({
    url: '/song/detail',
    method: 'get',
    params: { ids },
  }).then(data => {
    data.songs = mapTrackPlayableStatus(data.songs, data.privileges);
    return data;
  });
}

export function getLyric(id) {
  return request({
    url: '/lyric',
    method: 'get',
    params: { id },
  });
}

export function likeATrack(params) {
  return request({
    url: '/like',
    method: 'get',
    params: { ...params, timestamp: Date.now() },
  });
}
