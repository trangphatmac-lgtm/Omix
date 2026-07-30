import request from '@/utils/request';

export function loginQrCodeKey() {
  return request({
    url: '/login/qr/key',
    method: 'get',
    params: { timestamp: Date.now() },
  });
}

export function loginQrCodeCheck(key) {
  return request({
    url: '/login/qr/check',
    method: 'get',
    params: { key, timestamp: Date.now() },
  });
}

export function refreshCookie() {
  return request({ url: '/login/refresh', method: 'post' });
}

export function logout() {
  return request({ url: '/logout', method: 'post' });
}
