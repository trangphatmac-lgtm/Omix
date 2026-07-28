'use strict';

const assert = require('assert');
const childProcess = require('child_process');
const http = require('http');
const net = require('net');
const path = require('path');

async function freePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const {port} = server.address();
      server.close(error => (error ? reject(error) : resolve(port)));
    });
  });
}

async function request(port, pathname, token) {
  return new Promise((resolve, reject) => {
    const headers = token ? {'x-omix-music-token': token} : {};
    const req = http.get(
      {host: '127.0.0.1', port, path: pathname, headers},
      response => {
        const chunks = [];
        response.on('data', chunk => chunks.push(chunk));
        response.on('end', () =>
          resolve({
            status: response.statusCode,
            body: Buffer.concat(chunks).toString('utf8'),
          })
        );
      }
    );
    req.once('error', reject);
  });
}

async function waitForHealth(port, token) {
  for (let attempt = 0; attempt < 50; attempt++) {
    try {
      const response = await request(port, '/healthz', token);
      if (response.status === 200) return response;
    } catch {
      // The child may still be binding its loopback socket.
    }
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error('sidecar did not become healthy');
}

async function main() {
  const port = await freePort();
  const token = 'test-token-'.padEnd(64, '0');
  const child = childProcess.spawn(process.execPath, [path.join(__dirname, 'index.js')], {
    env: {
      ...process.env,
      NODE_ENV: 'test',
      OMIX_MUSIC_PORT: String(port),
      OMIX_MUSIC_TOKEN: token,
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });

  try {
    const health = await waitForHealth(port, token);
    assert.equal(JSON.parse(health.body).apiVersion, '4.29.7');
    assert.equal((await request(port, '/healthz')).status, 401);
    assert.equal((await request(port, '/not-enabled', token)).status, 404);
  } finally {
    child.kill('SIGTERM');
    await new Promise(resolve => {
      child.once('exit', resolve);
      setTimeout(() => {
        child.kill('SIGKILL');
        resolve();
      }, 3000).unref();
    });
  }
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
