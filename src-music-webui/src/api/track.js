import request from '@/utils/request';
import { mapTrackPlayableStatus } from '@/utils/common';

export function getMP3(id) {
  return request({
    url: '/song/url',
    method: 'get',
    params: { id, br: 320000 },
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
