import request from '@/utils/request';
import { mapTrackPlayableStatus } from '@/utils/common';
import { isAccountLoggedIn } from '@/utils/auth';
import { getTrackDetail } from '@/api/track';

export function getArtist(id) {
  return request({
    url: '/artists',
    method: 'get',
    params: { id, timestamp: Date.now() },
  }).then(async data => {
    if (!isAccountLoggedIn()) {
      const ids = data.hotSongs.map(track => track.id).join(',');
      const tracks = await getTrackDetail(ids);
      data.hotSongs = tracks.songs;
    } else {
      data.hotSongs = mapTrackPlayableStatus(data.hotSongs);
    }
    return data;
  });
}

export function getArtistAlbum(params) {
  return request({ url: '/artist/album', method: 'get', params });
}

export function toplistOfArtists(type = null) {
  return request({
    url: '/toplist/artist',
    method: 'get',
    params: type ? { type } : {},
  });
}
