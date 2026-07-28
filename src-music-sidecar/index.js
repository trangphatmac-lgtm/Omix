'use strict';

const express = require('express');
const decode = require('safe-decode-uri-component');
const fs = require('fs');
const os = require('os');
const path = require('path');

const anonymousToken = path.join(os.tmpdir(), 'anonymous_token');
if (!fs.existsSync(anonymousToken)) {
  fs.writeFileSync(anonymousToken, '', 'utf8');
}

const request = require('@neteaseapireborn/api/util/request');
const {cookieToJson} = require('@neteaseapireborn/api/util');

const TOKEN_HEADER = 'x-omix-music-token';
const token = process.env.OMIX_MUSIC_TOKEN;
const port = Number(process.env.OMIX_MUSIC_PORT);

if (!token || token.length < 32) {
  throw new Error('OMIX_MUSIC_TOKEN is missing');
}
if (!Number.isInteger(port) || port < 1 || port > 65535) {
  throw new Error('OMIX_MUSIC_PORT is invalid');
}

const definitions = [
  ['/login/qr/key', 'login_qr_key'],
  ['/login/qr/create', 'login_qr_create'],
  ['/login/qr/check', 'login_qr_check'],
  ['/login/refresh', 'login_refresh'],
  ['/logout', 'logout'],
  ['/personalized', 'personalized'],
  ['/recommend/resource', 'recommend_resource'],
  ['/recommend/songs', 'recommend_songs'],
  ['/toplist', 'toplist'],
  ['/toplist/artist', 'toplist_artist'],
  ['/top/playlist', 'top_playlist'],
  ['/top/playlist/highquality', 'top_playlist_highquality'],
  ['/album/new', 'album_new'],
  ['/search', 'search'],
  ['/playlist/detail', 'playlist_detail'],
  ['/album', 'album'],
  ['/artists', 'artists'],
  ['/artist/album', 'artist_album'],
  ['/user/account', 'user_account'],
  ['/user/playlist', 'user_playlist'],
  ['/song/detail', 'song_detail'],
  ['/song/url', 'song_url'],
  ['/lyric', 'lyric'],
].map(([route, moduleName]) => ({
  route,
  module: require(`@neteaseapireborn/api/module/${moduleName}`),
}));

function parseCookies(header) {
  const result = {};
  for (const pair of (header || '').split(/;\s+/)) {
    const split = pair.indexOf('=');
    if (split < 1) continue;
    result[decode(pair.slice(0, split)).trim()] =
      decode(pair.slice(split + 1)).trim();
  }
  return result;
}

function normalizeCookieInput(value) {
  return typeof value === 'string' ? cookieToJson(decode(value)) : value;
}

const app = express();
app.disable('x-powered-by');
app.set('trust proxy', false);
app.use(express.json({limit: '4mb'}));
app.use(express.urlencoded({extended: false, limit: '4mb'}));
app.use((req, res, next) => {
  if (req.get(TOKEN_HEADER) !== token) {
    res.status(401).json({code: 401, message: 'Unauthorized'});
    return;
  }
  next();
});

app.get('/healthz', (_req, res) => {
  res.json({
    status: 'ready',
    apiVersion: require('@neteaseapireborn/api/package.json').version,
    nodeVersion: process.version,
  });
});

for (const definition of definitions) {
  app.all(definition.route, async (req, res) => {
    const query = {
      cookie: parseCookies(req.get('cookie')),
      ...req.query,
      ...req.body,
    };
    delete query.proxy;
    delete query.realIP;
    delete query.domain;
    delete query.ua;
    delete query.crypto;
    if (query.cookie) query.cookie = normalizeCookieInput(query.cookie);

    try {
      const moduleResponse = await definition.module(query, (...params) => {
        const forwarded = [...params];
        forwarded[2] = {
          ...(forwarded[2] || {}),
          proxy: false,
          realIP: undefined,
          domain: '',
        };
        return request(...forwarded);
      });
      if (Array.isArray(moduleResponse.cookie) && moduleResponse.cookie.length) {
        res.append('Set-Cookie', moduleResponse.cookie);
      }
      res.status(moduleResponse.status || 200).send(moduleResponse.body);
    } catch (error) {
      const status = Number(error?.status) || 502;
      const body = error?.body || {
        code: status,
        message: 'Netease API request failed',
      };
      if (body.code === '301') body.msg = '需要登录';
      res.status(status).send(body);
    }
  });
}

app.use((_req, res) => {
  res.status(404).json({code: 404, message: 'Route is not enabled'});
});

const server = app.listen(port, '127.0.0.1', () => {
  process.stdout.write(
    `${JSON.stringify({event: 'ready', port, pid: process.pid})}\n`
  );
});

function shutdown() {
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(1), 3000).unref();
}

process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
process.on('uncaughtException', error => {
  process.stderr.write(`sidecar error: ${error?.message || error}\n`);
  process.exit(1);
});
