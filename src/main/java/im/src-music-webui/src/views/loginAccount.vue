<template>
  <div class="login">
    <img class="logo" src="/music/img/logos/netease-music.png" />
    <h1>扫码登录网易云音乐</h1>
    <div class="qr-code">
      <img v-if="qrCodeSvg" :src="qrCodeSvg" />
      <span v-else>正在生成二维码…</span>
    </div>
    <p>{{ information }}</p>
    <button v-if="expired" @click="getQrCodeKey">刷新二维码</button>
  </div>
</template>

<script>
import QRCode from 'qrcode';
import {
  loginQrCodeKey,
  loginQrCodeCheck,
} from '@/api/auth';
import { setCookies } from '@/utils/auth';
import NProgress from 'nprogress';

export default {
  name: 'LoginAccount',
  data() {
    return {
      key: '',
      qrCodeSvg: '',
      timer: null,
      expired: false,
      information: '打开网易云音乐 App 扫码',
    };
  },
  created() {
    this.getQrCodeKey();
  },
  beforeDestroy() {
    window.clearInterval(this.timer);
  },
  methods: {
    async getQrCodeKey() {
      window.clearInterval(this.timer);
      this.expired = false;
      this.qrCodeSvg = '';
      this.information = '正在生成二维码…';
      try {
        const result = await loginQrCodeKey();
        if (result.code !== 200) throw new Error(result.message || '二维码创建失败');
        this.key = result.data.unikey;
        const svg = await QRCode.toString(
          `https://music.163.com/login?codekey=${this.key}`,
          {
            width: 208,
            margin: 1,
            color: { dark: '#335eea', light: '#00000000' },
            type: 'svg',
          }
        );
        this.qrCodeSvg = `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
        this.information = '打开网易云音乐 App 扫码';
        this.timer = window.setInterval(this.checkQrCode, 1200);
      } catch (error) {
        this.information = error?.message || '二维码创建失败';
        this.expired = true;
      } finally {
        NProgress.done();
      }
    },
    async checkQrCode() {
      if (!this.key) return;
      try {
        const result = await loginQrCodeCheck(this.key);
        if (result.code === 800) {
          window.clearInterval(this.timer);
          this.expired = true;
          this.information = '二维码已过期';
        } else if (result.code === 802) {
          this.information = '扫描成功，请在手机上确认';
        } else if (result.code === 801) {
          this.information = '打开网易云音乐 App 扫码';
        } else if (result.code === 803) {
          window.clearInterval(this.timer);
          this.information = '登录成功，正在加载音乐库…';
          const cookies = Array.isArray(result.cookie)
            ? result.cookie.map(cookie => cookie.replaceAll(' HTTPOnly', ''))
            : result.cookie.replaceAll(' HTTPOnly', '');
          await setCookies(cookies);
          this.$store.commit('updateData', {
            key: 'loginMode',
            value: 'account',
          });
          await this.$store.dispatch('fetchUserProfile');
          await this.$store.dispatch('fetchLikedPlaylist');
          this.$router.push('/library');
        }
      } catch (error) {
        this.information = error?.message || '检查登录状态失败';
      }
    },
  },
};
</script>

<style lang="scss" scoped>
.login {
  display: flex;
  min-height: calc(100vh - 192px);
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--color-text);
}

.logo {
  width: 64px;
  height: 64px;
}

h1 {
  margin: 20px 0;
  font-size: 26px;
}

.qr-code {
  display: grid;
  width: 224px;
  height: 224px;
  place-items: center;
  border-radius: 18px;
  background: var(--color-secondary-bg);
}

.qr-code img {
  width: 208px;
  height: 208px;
}

p {
  opacity: 0.7;
}

button {
  padding: 10px 18px;
  border-radius: 10px;
  color: var(--color-primary);
  background: var(--color-primary-bg);
}
</style>
