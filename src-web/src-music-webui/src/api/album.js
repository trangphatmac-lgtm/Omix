import request from '@/utils/request';
import { mapTrackPlayableStatus } from '@/utils/common';

export function getAlbum(id) {
  return request({
    url: '/album',
    method: 'get',
    params: { id },
  }).then(data => {
    data.songs = mapTrackPlayableStatus(data.songs);
    return data;
  });
}

export function newAlbums(params) {
  return request({ url: '/album/new', method: 'get', params });
}
