<template>
  <div class="library-page">
    <h1>
      <img
        v-if="data.user.avatarUrl"
        class="avatar"
        :src="data.user.avatarUrl | resizeImage(128)"
        loading="lazy"
      />
      {{ data.user.nickname || '我的音乐库' }}
    </h1>

    <div v-if="liked.playlists.length">
      <h2>歌单</h2>
      <CoverRow
        :items="liked.playlists"
        type="playlist"
        sub-text="creator"
        :show-play-button="true"
      />
    </div>
    <div v-else class="empty">正在加载歌单，或该账户暂无歌单。</div>
  </div>
</template>

<script>
import { mapState } from 'vuex';
import CoverRow from '@/components/CoverRow.vue';
import NProgress from 'nprogress';

export default {
  name: 'Library',
  components: { CoverRow },
  computed: {
    ...mapState(['data', 'liked']),
  },
  created() {
    NProgress.done();
    if (!this.liked.playlists.length) {
      this.$store.dispatch('fetchLikedPlaylist');
    }
  },
};
</script>

<style lang="scss" scoped>
.library-page {
  margin-top: 32px;
  color: var(--color-text);
}

h1 {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 40px;
}

.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
}

.empty {
  padding: 32px;
  border-radius: 16px;
  opacity: 0.68;
  background: var(--color-secondary-bg);
}
</style>
