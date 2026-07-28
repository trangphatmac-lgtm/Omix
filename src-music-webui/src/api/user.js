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
