<template>
  <div class="settings-page">
    <h1>{{ $t('nav.settings') || '设置' }}</h1>

    <section>
      <h2>外观</h2>
      <label>
        显示模式
        <select v-model="appearance">
          <option value="auto">跟随系统</option>
          <option value="light">浅色</option>
          <option value="dark">深色</option>
        </select>
      </label>
      <label>
        主题色
        <select v-model="themeColor">
          <option value="default">默认蓝</option>
          <option value="sunset">日落</option>
          <option value="ocean">海洋</option>
          <option value="forest">森林</option>
        </select>
      </label>
      <label>
        歌词字号
        <input v-model.number="lyricFontSize" type="range" min="18" max="42" />
        <span>{{ lyricFontSize }} px</span>
      </label>
    </section>

    <section>
      <h2>账户</h2>
      <p v-if="loggedIn">{{ data.user.nickname || '网易云音乐用户' }}</p>
      <p v-else>尚未登录网易云音乐</p>
      <button v-if="loggedIn" @click="logout">退出登录</button>
      <button v-else @click="$router.push('/login/account')">二维码登录</button>
    </section>

    <section class="about">
      <h2>关于</h2>
      <p>Omix Music · YesPlayMusic df075cca 适配版</p>
      <p>首版仅提供 MP3 在线播放，不包含 MV、云盘、解灰或离线缓存。</p>
    </section>
  </div>
</template>

<script>
import { mapState } from 'vuex';
import { changeAppearance, changeThemeColor } from '@/utils/common';
import { doLogout, isAccountLoggedIn } from '@/utils/auth';

export default {
  name: 'Settings',
  computed: {
    ...mapState(['settings', 'data']),
    loggedIn() {
      return isAccountLoggedIn();
    },
    appearance: {
      get() {
        return this.settings.appearance || 'auto';
      },
      set(value) {
        this.$store.commit('updateSettings', { key: 'appearance', value });
        changeAppearance(value);
        changeThemeColor(this.themeColor);
      },
    },
    themeColor: {
      get() {
        return this.settings.themeColor || 'default';
      },
      set(value) {
        this.$store.commit('updateSettings', { key: 'themeColor', value });
        changeThemeColor(value);
      },
    },
    lyricFontSize: {
      get() {
        return this.settings.lyricFontSize || 28;
      },
      set(value) {
        this.$store.commit('changeLyricFontSize', value);
      },
    },
  },
  methods: {
    logout() {
      if (!window.confirm('确定要退出登录吗？')) return;
      doLogout();
      this.$router.push('/');
    },
  },
};
</script>

<style lang="scss" scoped>
.settings-page {
  max-width: 720px;
  margin: 32px auto;
  color: var(--color-text);
}

h1 {
  margin-bottom: 32px;
}

section {
  margin-bottom: 24px;
  padding: 24px;
  border-radius: 16px;
  background: var(--color-secondary-bg);
}

h2 {
  margin: 0 0 20px;
}

label {
  display: grid;
  grid-template-columns: 120px minmax(180px, 1fr) auto;
  align-items: center;
  gap: 16px;
  margin: 16px 0;
}

select,
button {
  padding: 10px 14px;
  border-radius: 10px;
  color: var(--color-text);
  background: var(--color-primary-bg);
}

button {
  color: var(--color-primary);
  font-weight: 600;
}

.about p {
  opacity: 0.7;
}

@media (max-width: 900px), (max-height: 540px) {
  .settings-page {
    margin-top: 8px;
  }

  section {
    padding: 16px;
  }
}
</style>
