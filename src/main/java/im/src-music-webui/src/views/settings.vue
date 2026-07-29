<template>
  <div class="settings-page">
    <div class="container">
      <div v-if="showUserInfo" class="user">
        <div class="profile">
          <img
            class="avatar"
            :src="data.user.avatarUrl | resizeImage(128)"
            loading="lazy"
          />
          <div class="info">
            <div class="nickname">{{ data.user.nickname }}</div>
            <div class="extra-info">
              <span v-if="data.user.vipType" class="vip">CVIP</span>
              <span>{{ data.user.vipType ? '黑胶VIP' : data.user.signature }}</span>
            </div>
          </div>
        </div>
        <button class="logout" @click="logout">
          <svg-icon icon-class="logout" />
          {{ $t('settings.logout') }}
        </button>
      </div>

      <div v-else class="user logged-out">
        <div>
          <div class="nickname">网易云音乐</div>
          <div class="extra-info">登录后同步音乐库与收藏</div>
        </div>
        <button class="logout" @click="$router.push('/login/account')">
          <svg-icon icon-class="login" />
          登录
        </button>
      </div>

      <div class="item">
        <div class="left">
          <div class="title">{{ $t('settings.language') }}</div>
        </div>
        <div class="right">
          <select v-model="lang">
            <option value="en">🇬🇧 English</option>
            <option value="tr">🇹🇷 Türkçe</option>
            <option value="zh-CN">🇨🇳 简体中文</option>
            <option value="zh-TW">繁體中文</option>
          </select>
        </div>
      </div>

      <div class="item">
        <div class="left">
          <div class="title">{{ $t('settings.appearance.text') }}</div>
        </div>
        <div class="right">
          <select v-model="appearance">
            <option value="auto">{{ $t('settings.appearance.auto') }}</option>
            <option value="light">
              🌞 {{ $t('settings.appearance.light') }}
            </option>
            <option value="dark">
              🌚 {{ $t('settings.appearance.dark') }}
            </option>
          </select>
        </div>
      </div>

      <div class="item">
        <div class="left">
          <div class="title">{{ $t('settings.themeColor.text') }}</div>
        </div>
        <div class="right">
          <select v-model="themeColor">
            <option value="default">{{ $t('settings.themeColor.default') }}</option>
            <option value="sunset">{{ $t('settings.themeColor.sunset') }}</option>
            <option value="ocean">{{ $t('settings.themeColor.ocean') }}</option>
            <option value="forest">{{ $t('settings.themeColor.forest') }}</option>
          </select>
        </div>
      </div>

      <div class="item">
        <div class="left">
          <div class="title">
            {{ $t('settings.MusicGenrePreference.text') }}
          </div>
        </div>
        <div class="right">
          <select v-model="musicLanguage">
            <option value="all">
              {{ $t('settings.MusicGenrePreference.none') }}
            </option>
            <option value="zh">
              {{ $t('settings.MusicGenrePreference.mandarin') }}
            </option>
            <option value="ea">
              {{ $t('settings.MusicGenrePreference.western') }}
            </option>
            <option value="jp">
              {{ $t('settings.MusicGenrePreference.japanese') }}
            </option>
            <option value="kr">
              {{ $t('settings.MusicGenrePreference.korean') }}
            </option>
          </select>
        </div>
      </div>

      <div class="item">
        <div class="left">
          <div class="title">{{ $t('settings.musicQuality.text') }}</div>
        </div>
        <div class="right">
          <select v-model="musicQuality">
            <option :value="128000">
              {{ $t('settings.musicQuality.low') }} - 128Kbps
            </option>
            <option :value="192000">
              {{ $t('settings.musicQuality.medium') }} - 192Kbps
            </option>
            <option :value="320000">
              {{ $t('settings.musicQuality.high') }} - 320Kbps
            </option>
            <option value="flac">
              {{ $t('settings.musicQuality.lossless') }} - FLAC
            </option>
            <option :value="999000">Hi-Res</option>
          </select>
        </div>
      </div>

      <h3>缓存</h3>
      <div class="item">
        <div class="left">
          <div class="title">{{ $t('settings.automaticallyCacheSongs') }}</div>
          <div class="description">播放时自动保存音频，供下次快速加载</div>
        </div>
        <div class="right">
          <ToggleSwitch
            id="automatically-cache-songs"
            v-model="automaticallyCacheSongs"
          />
        </div>
      </div>

      <div class="item">
        <div class="left">
          <div class="title">{{ $t('settings.cacheLimit.text') }}</div>
        </div>
        <div class="right">
          <select v-model="cacheLimit" :disabled="!automaticallyCacheSongs">
            <option :value="false">{{ $t('settings.cacheLimit.none') }}</option>
            <option :value="512">500MB</option>
            <option :value="1024">1GB</option>
            <option :value="2048">2GB</option>
            <option :value="4096">4GB</option>
            <option :value="8192">8GB</option>
          </select>
        </div>
      </div>

      <div class="item">
        <div class="left">
          <div class="title">
            {{
              $t('settings.cacheCount', {
                song: tracksCache.length,
                size: formattedCacheSize,
              })
            }}
          </div>
        </div>
        <div class="right">
          <button class="secondary-button" @click="clearCache">
            {{ $t('settings.clearSongsCache') }}
          </button>
        </div>
      </div>

      <h3>{{ $t('settings.lyric') }}</h3>
      <div class="item">
        <div class="left">
          <div class="title">{{ $t('settings.showLyricsTranslation') }}</div>
        </div>
        <div class="right">
          <ToggleSwitch
            id="show-lyrics-translation"
            v-model="showLyricsTranslation"
          />
        </div>
      </div>

      <div class="item">
        <div class="left">
          <div class="title">{{ $t('settings.lyricsBackground.text') }}</div>
        </div>
        <div class="right">
          <select v-model="lyricsBackground">
            <option :value="false">
              {{ $t('settings.lyricsBackground.off') }}
            </option>
            <option :value="true">
              {{ $t('settings.lyricsBackground.on') }}
            </option>
            <option value="blur">模糊封面</option>
            <option value="dynamic">
              {{ $t('settings.lyricsBackground.dynamic') }}
            </option>
          </select>
        </div>
      </div>

      <div class="item">
        <div class="left">
          <div class="title">{{ $t('settings.showLyricsTime') }}</div>
        </div>
        <div class="right">
          <ToggleSwitch id="show-lyrics-time" v-model="showLyricsTime" />
        </div>
      </div>

      <div class="item">
        <div class="left">
          <div class="title">{{ $t('settings.lyricFontSize.text') }}</div>
        </div>
        <div class="right">
          <select v-model="lyricFontSize">
            <option :value="16">
              {{ $t('settings.lyricFontSize.small') }} - 16px
            </option>
            <option :value="22">
              {{ $t('settings.lyricFontSize.medium') }} - 22px
            </option>
            <option :value="28">
              {{ $t('settings.lyricFontSize.large') }} - 28px
            </option>
            <option :value="36">
              {{ $t('settings.lyricFontSize.xlarge') }} - 36px
            </option>
          </select>
        </div>
      </div>

      <div class="footer">
        <div>Omix Music · YesPlayMusic</div>
        <div class="version">v{{ version }}</div>
      </div>
    </div>
  </div>
</template>

<script>
import { mapActions, mapState } from 'vuex';
import { doLogout, isLooseLoggedIn } from '@/utils/auth';
import { changeAppearance, changeThemeColor } from '@/utils/common';
import {
  clearAudioCache,
  getAudioCacheStats,
} from '@/utils/audioCache';
import SvgIcon from '@/components/SvgIcon.vue';
import ToggleSwitch from '@/components/ToggleSwitch.vue';
import pkg from '../../package.json';

export default {
  name: 'Settings',
  components: { SvgIcon, ToggleSwitch },
  data() {
    return {
      tracksCache: { length: 0, bytes: 0 },
    };
  },
  computed: {
    ...mapState(['settings', 'data']),
    version() {
      return pkg.version;
    },
    showUserInfo() {
      return isLooseLoggedIn() && this.data.user.nickname;
    },
    formattedCacheSize() {
      const bytes = this.tracksCache.bytes;
      if (!bytes) return '0 KB';
      if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
      return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
    },
    lang: {
      get() {
        return this.settings.lang;
      },
      set(value) {
        this.$i18n.locale = value;
        this.$store.commit('changeLang', value);
      },
    },
    appearance: {
      get() {
        return this.settings.appearance ?? 'auto';
      },
      set(value) {
        this.setSetting('appearance', value);
        changeAppearance(value);
        changeThemeColor(this.themeColor);
      },
    },
    themeColor: {
      get() {
        return this.settings.themeColor ?? 'default';
      },
      set(value) {
        this.setSetting('themeColor', value);
        changeThemeColor(value);
      },
    },
    musicLanguage: {
      get() {
        return this.settings.musicLanguage ?? 'all';
      },
      set(value) {
        this.setSetting('musicLanguage', value);
      },
    },
    musicQuality: {
      get() {
        return this.settings.musicQuality ?? 320000;
      },
      set(value) {
        this.$store.commit('changeMusicQuality', value);
        this.clearCache();
      },
    },
    automaticallyCacheSongs: {
      get() {
        return this.settings.automaticallyCacheSongs ?? false;
      },
      set(value) {
        this.setSetting('automaticallyCacheSongs', value);
      },
    },
    cacheLimit: {
      get() {
        return this.settings.cacheLimit ?? 4096;
      },
      set(value) {
        this.setSetting('cacheLimit', value);
      },
    },
    showLyricsTranslation: {
      get() {
        return this.settings.showLyricsTranslation ?? true;
      },
      set(value) {
        this.setSetting('showLyricsTranslation', value);
      },
    },
    lyricsBackground: {
      get() {
        return this.settings.lyricsBackground ?? true;
      },
      set(value) {
        this.setSetting('lyricsBackground', value);
      },
    },
    showLyricsTime: {
      get() {
        return this.settings.showLyricsTime ?? false;
      },
      set(value) {
        this.setSetting('showLyricsTime', value);
      },
    },
    lyricFontSize: {
      get() {
        return this.settings.lyricFontSize ?? 28;
      },
      set(value) {
        this.$store.commit('changeLyricFontSize', value);
      },
    },
  },
  created() {
    this.refreshCacheStats();
  },
  activated() {
    this.refreshCacheStats();
  },
  methods: {
    ...mapActions(['showToast']),
    setSetting(key, value) {
      this.$store.commit('updateSettings', { key, value });
    },
    async refreshCacheStats() {
      this.tracksCache = await getAudioCacheStats();
    },
    async clearCache() {
      await clearAudioCache();
      await this.refreshCacheStats();
      this.showToast('歌曲缓存已清除');
    },
    logout() {
      if (!window.confirm('确定要退出登录吗？')) return;
      doLogout();
      this.$router.push({ name: 'home' });
    },
  },
};
</script>

<style lang="scss" scoped>
.settings-page {
  display: flex;
  justify-content: center;
  color: var(--color-text);
}

.container {
  width: min(720px, 100%);
  margin-top: 24px;
}

.user {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  margin-bottom: 48px;
  background: var(--color-secondary-bg);
  border-radius: 16px;

  .profile {
    display: flex;
    align-items: center;
  }

  .avatar {
    width: 64px;
    height: 64px;
    object-fit: cover;
    border-radius: 50%;
  }

  .info {
    margin-left: 24px;
  }

  .nickname {
    margin-bottom: 3px;
    font-size: 20px;
    font-weight: 600;
  }

  .extra-info {
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 13px;
    opacity: 0.68;
  }

  .vip {
    padding: 1px 4px;
    color: #e8b5ab;
    font-size: 10px;
    font-weight: 700;
    background: #331b19;
    border-radius: 3px;
  }
}

.logged-out {
  padding: 24px;

  .extra-info {
    margin-top: 4px;
  }
}

.logout {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  color: var(--color-text);
  font-size: 18px;
  font-weight: 600;
  border-radius: 10px;
  opacity: 0.68;
  transition: 0.2s;

  .svg-icon {
    width: 18px;
    height: 18px;
  }

  &:hover {
    color: var(--color-primary);
    background: var(--color-primary-bg);
    opacity: 1;
  }
}

h3 {
  padding-bottom: 12px;
  margin: 48px 0 24px;
  font-size: 26px;
  border-bottom: 1px solid rgba(128, 128, 128, 0.18);
}

.item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 42px;
  margin: 18px 0;

  .left {
    padding-right: 24px;
  }

  .title {
    font-size: 16px;
    font-weight: 500;
    opacity: 0.78;
  }

  .description {
    margin-top: 5px;
    font-size: 13px;
    opacity: 0.52;
  }
}

select,
.secondary-button {
  min-width: 192px;
  max-width: 320px;
  padding: 9px 36px 9px 12px;
  color: var(--color-text);
  font-size: 14px;
  font-weight: 600;
  background: var(--color-secondary-bg);
  border: 0;
  border-radius: 8px;
  appearance: none;
  transition: 0.2s;

  &:focus {
    color: var(--color-primary);
    background: var(--color-primary-bg);
    outline: none;
  }

  &:disabled {
    opacity: 0.4;
  }
}

.secondary-button {
  min-width: auto;
  padding-right: 12px;
  cursor: pointer;

  &:hover {
    color: var(--color-primary);
    background: var(--color-primary-bg);
  }
}

.footer {
  margin-top: 88px;
  text-align: center;
  font-weight: 600;
  opacity: 0.56;

  .version {
    margin-top: 5px;
    font-size: 13px;
  }
}

@media (max-width: 680px) {
  .user {
    margin-bottom: 32px;
  }

  .item {
    align-items: flex-start;
    gap: 12px;
    flex-direction: column;
    margin: 24px 0;

    .right,
    select {
      width: 100%;
    }

    select {
      max-width: none;
      box-sizing: border-box;
    }
  }
}
</style>

<style lang="scss">
.settings-page .toggle {
  position: relative;
  width: 52px;
  height: 32px;

  input {
    position: absolute;
    opacity: 0;
  }

  label {
    position: absolute;
    inset: 0;
    background: var(--color-secondary-bg);
    border-radius: 8px;
    cursor: pointer;
    transition: 0.25s;

    &::after {
      position: absolute;
      top: 6px;
      left: 6px;
      width: 20px;
      height: 20px;
      background: #fff;
      border-radius: 6px;
      box-shadow: 0 3px 7px rgba(0, 0, 0, 0.15);
      content: '';
      transition: 0.25s cubic-bezier(0.54, 1.6, 0.5, 1);
    }
  }

  input:checked + label {
    background: var(--color-primary-gradient);

    &::after {
      left: 26px;
    }
  }

  input:focus-visible + label {
    outline: 2px solid var(--color-primary);
    outline-offset: 3px;
  }
}
</style>
