'use strict';

const express = require('express');
const decode = require('safe-decode-uri-component');
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const {spawn, spawnSync} = require('child_process');

const anonymousToken = path.join(os.tmpdir(), 'anonymous_token');
if (!fs.existsSync(anonymousToken)) {
  fs.writeFileSync(anonymousToken, '', 'utf8');
}

const request = require('@neteaseapireborn/api/util/request');
const {cookieToJson} = require('@neteaseapireborn/api/util');

const TOKEN_HEADER = 'x-omix-music-token';
const token = process.env.OMIX_MUSIC_TOKEN;
const port = Number(process.env.OMIX_MUSIC_PORT);
const transcodeDirectory = path.join(
  os.tmpdir(),
  `omix-music-transcoded-${process.getuid?.() ?? 'user'}`,
);
const transcodeJobs = new Map();

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
  ['/user/record', 'user_record'],
  ['/likelist', 'likelist'],
  ['/album/sublist', 'album_sublist'],
  ['/artist/sublist', 'artist_sublist'],
  ['/mv/sublist', 'mv_sublist'],
  ['/user/cloud', 'user_cloud'],
  ['/user/cloud/del', 'user_cloud_del'],
  ['/like', 'like'],
  ['/playlist/create', 'playlist_create'],
  ['/playlist/tracks', 'playlist_tracks'],
  ['/playmode/intelligence/list', 'playmode_intelligence_list'],
  ['/song/detail', 'song_detail'],
  ['/song/url', 'song_url'],
  ['/lyric', 'lyric'],
].map(([route, moduleName]) => ({
  route,
  module: require(`@neteaseapireborn/api/module/${moduleName}`),
}));
const songUrlModule = require('@neteaseapireborn/api/module/song_url');

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

function forwardNeteaseRequest(...params) {
  const forwarded = [...params];
  forwarded[2] = {
    ...(forwarded[2] || {}),
    proxy: false,
    realIP: undefined,
    domain: '',
  };
  return request(...forwarded);
}

function findFfmpeg() {
  const configured = process.env.OMIX_FFMPEG_PATH;
  const candidates = [
    configured,
    process.platform === 'win32' ? 'ffmpeg.exe' : 'ffmpeg',
    '/opt/homebrew/bin/ffmpeg',
    '/usr/local/bin/ffmpeg',
    '/usr/bin/ffmpeg',
  ].filter(Boolean);
  for (const candidate of candidates) {
    const result = spawnSync(candidate, ['-version'], {
      stdio: 'ignore',
      timeout: 5000,
    });
    if (!result.error && result.status === 0) return candidate;
  }
  return null;
}

const ffmpeg = findFfmpeg();

function transcodeKey(trackID, cookie) {
  const account = crypto
    .createHash('sha256')
    .update(String(cookie?.MUSIC_U || 'anonymous'))
    .digest('hex')
    .slice(0, 16);
  return `${account}-${trackID}`;
}

async function trimTranscodeCache() {
  const maxBytes = 512 * 1024 * 1024;
  const entries = [];
  let total = 0;
  for (const name of await fs.promises.readdir(transcodeDirectory)) {
    if (!name.endsWith('.mp3')) continue;
    const file = path.join(transcodeDirectory, name);
    const stat = await fs.promises.stat(file);
    total += stat.size;
    entries.push({file, size: stat.size, mtime: stat.mtimeMs});
  }
  entries.sort((left, right) => left.mtime - right.mtime);
  for (const entry of entries) {
    if (total <= maxBytes) break;
    await fs.promises.unlink(entry.file).catch(() => {});
    total -= entry.size;
  }
}

async function transcodeTrack(trackID, cookie) {
  if (!ffmpeg) throw new Error('FFmpeg is unavailable');
  await fs.promises.mkdir(transcodeDirectory, {recursive: true});
  await fs.promises.chmod(transcodeDirectory, 0o700).catch(() => {});
  const key = transcodeKey(trackID, cookie);
  const destination = path.join(transcodeDirectory, `${key}.mp3`);
  const existing = await fs.promises.stat(destination).catch(() => null);
  if (existing?.isFile() && existing.size > 1024) {
    await fs.promises.utimes(destination, new Date(), new Date());
    return destination;
  }
  if (transcodeJobs.has(key)) return transcodeJobs.get(key);

  const job = (async () => {
    const response = await songUrlModule(
      {id: trackID, br: 320000, cookie},
      forwardNeteaseRequest,
    );
    const source = response?.body?.data?.[0]?.url;
    if (!source) throw new Error('No playable cloud source');
    const partial = `${destination}.${process.pid}.${Date.now()}.part`;
    try {
      await new Promise((resolve, reject) => {
        const child = spawn(
          ffmpeg,
          [
            '-nostdin',
            '-hide_banner',
            '-loglevel',
            'error',
            '-y',
            '-i',
            source,
            '-map',
            '0:a:0',
            '-vn',
            '-codec:a',
            'libmp3lame',
            '-b:a',
            '192k',
            '-f',
            'mp3',
            partial,
          ],
          {stdio: ['ignore', 'ignore', 'pipe']},
        );
        let errorOutput = '';
        child.stderr.on('data', chunk => {
          errorOutput = (errorOutput + chunk.toString('utf8')).slice(-4096);
        });
        child.once('error', reject);
        child.once('exit', code => {
          if (code === 0) resolve();
          else reject(new Error(`FFmpeg exited with code ${code}: ${errorOutput}`));
        });
      });
      const stat = await fs.promises.stat(partial);
      if (stat.size <= 1024) throw new Error('Transcoded audio is empty');
      await fs.promises.rename(partial, destination);
      await fs.promises.chmod(destination, 0o600).catch(() => {});
      void trimTranscodeCache().catch(() => {});
      return destination;
    } finally {
      await fs.promises.unlink(partial).catch(() => {});
    }
  })().finally(() => transcodeJobs.delete(key));
  transcodeJobs.set(key, job);
  return job;
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

app.get('/audio/transcode', async (req, res) => {
  const trackID = String(req.query.id || '');
  if (!/^\d+$/.test(trackID)) {
    res.status(400).json({code: 400, message: 'A numeric track id is required'});
    return;
  }
  if (!ffmpeg) {
    res.status(503).json({code: 503, message: 'Audio transcoder is unavailable'});
    return;
  }
  const cookie = normalizeCookieInput(parseCookies(req.get('cookie')));
  try {
    const file = await transcodeTrack(trackID, cookie);
    res.type('audio/mpeg');
    res.sendFile(file);
  } catch {
    res.status(502).json({code: 502, message: 'Audio transcoding failed'});
  }
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
      const moduleResponse = await definition.module(
        query,
        forwardNeteaseRequest,
      );
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
