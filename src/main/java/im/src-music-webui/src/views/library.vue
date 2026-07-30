<template>
  <div v-show="show" ref="library" class="library-page">
    <h1>
      <img
        v-if="data.user.avatarUrl"
        class="avatar"
        :src="data.user.avatarUrl | resizeImage(128)"
        loading="lazy"
      />
      {{ data.user.nickname }}{{ $t('library.sLibrary') }}
    </h1>

    <div class="section-one">
      <div class="liked-songs" @click="goToLikedSongsList">
        <div class="top">
          <p v-if="pickedLyric.length">
            <span v-for="(line, index) in pickedLyric" :key="`${line}${index}`">
              {{ line }}<br />
            </span>
          </p>
          <p v-else class="lyric-placeholder">
            {{ $t('library.likedSongs') }}
          </p>
        </div>
        <div class="bottom">
          <div class="titles">
            <div class="title">{{ $t('library.likedSongs') }}</div>
            <div class="sub-title">
              {{ likedSongCount }} {{ $t('common.songs') }}
            </div>
          </div>
          <button aria-label="播放我喜欢的音乐" @click.stop="openPlayModeTabMenu">
            <svg-icon icon-class="play" />
          </button>
        </div>
      </div>

      <div class="songs">
        <TrackList
          :id="liked.playlists[0] ? liked.playlists[0].id : 0"
          :tracks="liked.songsWithDetails"
          :column-number="3"
          type="tracklist"
          dbclick-track-func="playPlaylistByID"
        />
      </div>
    </div>

    <div class="section-two">
      <div class="tabs-row">
        <div class="tabs">
          <div
            class="tab dropdown"
            :class="{ active: currentTab === 'playlists' }"
            @click="updateCurrentTab('playlists')"
          >
            <span class="text">{{ playlistFilterLabel }}</span>
            <span class="icon" @click.stop="openPlaylistTabMenu">
              <svg-icon icon-class="dropdown" />
            </span>
          </div>
          <button
            v-for="tab in tabs"
            :key="tab.name"
            class="tab"
            :class="{ active: currentTab === tab.name }"
            @click="updateCurrentTab(tab.name)"
          >
            {{ tab.label }}
          </button>
        </div>

        <button
          v-if="currentTab === 'playlists'"
          class="tab-button"
          @click="openAddPlaylistModal"
        >
          <svg-icon icon-class="plus" />{{ $t('library.newPlayList') }}
        </button>
        <button
          v-if="currentTab === 'cloudDisk'"
          class="tab-button"
          @click="selectUploadFiles"
        >
          <svg-icon icon-class="arrow-up-alt" />{{ $t('library.uploadSongs') }}
        </button>
      </div>

      <div v-show="currentTab === 'playlists'">
        <CoverRow
          v-if="filterPlaylists.length"
          :items="filterPlaylists"
          type="playlist"
          sub-text="creator"
        />
        <div v-else class="empty-state">{{ emptyTabText }}</div>
      </div>

      <div v-show="currentTab === 'albums'">
        <CoverRow
          v-if="liked.albums.length"
          :items="liked.albums"
          type="album"
          sub-text="artist"
        />
        <div v-else class="empty-state">{{ emptyTabText }}</div>
      </div>

      <div v-show="currentTab === 'artists'">
        <CoverRow
          v-if="liked.artists.length"
          :items="liked.artists"
          type="artist"
        />
        <div v-else class="empty-state">{{ emptyTabText }}</div>
      </div>

      <div v-show="currentTab === 'mvs'">
        <MvRow v-if="liked.mvs.length" :mvs="liked.mvs" />
        <div v-else class="empty-state">{{ emptyTabText }}</div>
      </div>

      <div v-show="currentTab === 'cloudDisk'">
        <TrackList
          v-if="liked.cloudDisk.length"
          :id="-8"
          :tracks="liked.cloudDisk"
          :column-number="3"
          type="cloudDisk"
          dbclick-track-func="playCloudDisk"
          :extra-context-menu-item="['removeTrackFromCloudDisk']"
        />
        <div v-else class="empty-state">{{ emptyTabText }}</div>
      </div>

      <div v-show="currentTab === 'playHistory'">
        <div class="history-switch">
          <button
            :class="{ selected: playHistoryMode === 'week' }"
            @click="playHistoryMode = 'week'"
          >
            {{ $t('library.playHistory.week') }}
          </button>
          <button
            :class="{ selected: playHistoryMode === 'all' }"
            @click="playHistoryMode = 'all'"
          >
            {{ $t('library.playHistory.all') }}
          </button>
        </div>
        <TrackList
          v-if="playHistoryList.length"
          :tracks="playHistoryList"
          :column-number="1"
          type="tracklist"
        />
        <div v-else class="empty-state">{{ emptyTabText }}</div>
      </div>
    </div>

    <input
      ref="cloudDiskUploadInput"
      type="file"
      accept="audio/*"
      hidden
      @change="uploadSongToCloudDisk"
    />

    <ContextMenu ref="playlistTabMenu">
      <div class="item" @click="changePlaylistFilter('all')">
        {{ $t('contextMenu.allPlaylists') }}
      </div>
      <hr />
      <div class="item" @click="changePlaylistFilter('mine')">
        {{ $t('contextMenu.minePlaylists') }}
      </div>
      <div class="item" @click="changePlaylistFilter('liked')">
        {{ $t('contextMenu.likedPlaylists') }}
      </div>
    </ContextMenu>

    <ContextMenu ref="playModeTabMenu">
      <div class="item" @click="playLikedSongs">
        {{ $t('library.likedSongs') }}
      </div>
      <hr />
      <div class="item" @click="playIntelligenceList">
        {{ $t('contextMenu.cardiacMode') }}
      </div>
    </ContextMenu>
  </div>
</template>

<script>
import { mapActions, mapMutations, mapState } from 'vuex';
import { getLyric } from '@/api/track';
import { uploadSong } from '@/api/user';
import { isAccountLoggedIn } from '@/utils/auth';
import { randomNum } from '@/utils/common';
import locale from '@/locale';
import NProgress from 'nprogress';

import ContextMenu from '@/components/ContextMenu.vue';
import CoverRow from '@/components/CoverRow.vue';
import MvRow from '@/components/MvRow.vue';
import SvgIcon from '@/components/SvgIcon.vue';
import TrackList from '@/components/TrackList.vue';

export default {
  name: 'Library',
  components: { ContextMenu, CoverRow, MvRow, SvgIcon, TrackList },
  data() {
    return {
      show: false,
      lyric: '',
      currentTab: 'playlists',
      playHistoryMode: 'week',
      loading: false,
      loadError: false,
    };
  },
  computed: {
    ...mapState(['data', 'liked']),
    tabs() {
      return [
        { name: 'albums', label: this.$t('library.albums') },
        { name: 'artists', label: this.$t('library.artists') },
        { name: 'mvs', label: this.$t('library.mvs') },
        { name: 'cloudDisk', label: this.$t('library.cloudDisk') },
        {
          name: 'playHistory',
          label: this.$t('library.playHistory.title'),
        },
      ];
    },
    pickedLyric() {
      const lines = this.lyric
        .split('\n')
        .map(line => line.replace(/^.*?]/, '').trim())
        .filter(
          line =>
            line &&
            !line.includes('作词') &&
            !line.includes('作曲') &&
            !line.includes('纯音乐')
        );
      if (!lines.length) return [];
      const count = Math.min(lines.length, 3);
      const start = randomNum(0, Math.max(0, lines.length - count));
      return lines.slice(start, start + count);
    },
    playlistFilter() {
      return this.data.libraryPlaylistFilter || 'all';
    },
    playlistFilterLabel() {
      return {
        all: this.$t('contextMenu.allPlaylists'),
        mine: this.$t('contextMenu.minePlaylists'),
        liked: this.$t('contextMenu.likedPlaylists'),
      }[this.playlistFilter];
    },
    filterPlaylists() {
      const playlists = this.liked.playlists.slice(1);
      const userID = this.data.user.userId;
      if (this.playlistFilter === 'mine') {
        return playlists.filter(p => p.creator?.userId === userID);
      }
      if (this.playlistFilter === 'liked') {
        return playlists.filter(p => p.creator?.userId !== userID);
      }
      return playlists;
    },
    playHistoryList() {
      return this.liked.playHistory?.[
        this.playHistoryMode === 'week' ? 'weekData' : 'allData'
      ] || [];
    },
    likedSongCount() {
      return Math.max(
        this.liked.songs.length,
        Number(this.liked.playlists[0]?.trackCount) || 0
      );
    },
    emptyTabText() {
      if (this.loading) return '正在加载…';
      return this.loadError ? '加载失败，请重试' : '这里暂时没有内容';
    },
  },
  created() {
    this.loadData();
  },
  activated() {
    this.$parent.$refs.scrollbar?.restorePosition();
    this.loadData();
  },
  methods: {
    ...mapActions(['showToast']),
    ...mapMutations(['updateData', 'updateModal']),
    async loadData() {
      if (this.loading) return;
      this.loading = true;
      this.loadError = false;
      if (!this.show) NProgress.start();
      try {
        await this.$store.dispatch('fetchLikedPlaylist');
        await this.$store.dispatch('fetchLikedSongsWithDetails');
        const results = await Promise.allSettled([
          this.$store.dispatch('fetchLikedAlbums'),
          this.$store.dispatch('fetchLikedArtists'),
          this.$store.dispatch('fetchLikedMVs'),
          this.$store.dispatch('fetchCloudDisk'),
          this.$store.dispatch('fetchPlayHistory'),
        ]);
        this.loadError = results.some(result => result.status === 'rejected');
        if (this.loadError) {
          this.showToast('部分音乐库内容加载失败，请重试');
        }
        this.getRandomLyric();
      } catch {
        this.loadError = true;
        this.showToast('音乐库加载失败，请重试');
      } finally {
        this.loading = false;
        this.show = true;
        NProgress.done();
      }
    },
    getRandomLyric() {
      if (!this.liked.songs.length) return;
      const id = this.liked.songs[
        randomNum(0, this.liked.songs.length - 1)
      ];
      getLyric(id).then(result => {
        this.lyric = result.lrc?.lyric || '';
      });
    },
    updateCurrentTab(tab) {
      if (!isAccountLoggedIn() && tab !== 'playlists') {
        this.showToast(locale.t('toast.needToLogin'));
        return;
      }
      this.currentTab = tab;
      this.$parent.$refs.main?.scrollTo({ top: 375, behavior: 'smooth' });
    },
    goToLikedSongsList() {
      this.$router.push('/library/liked-songs');
    },
    playLikedSongs() {
      const id = this.liked.playlists[0]?.id;
      if (id) this.$store.state.player.playPlaylistByID(id, 'first', true);
    },
    playIntelligenceList() {
      const id = this.liked.playlists[0]?.id;
      if (id) {
        this.$store.state.player.playIntelligenceListById(id, 'first', true);
      }
    },
    openAddPlaylistModal() {
      if (!isAccountLoggedIn()) {
        this.showToast(locale.t('toast.needToLogin'));
        return;
      }
      this.updateModal({
        modalName: 'newPlaylistModal',
        key: 'show',
        value: true,
      });
    },
    openPlaylistTabMenu(event) {
      this.$refs.playlistTabMenu.openMenu(event);
    },
    openPlayModeTabMenu(event) {
      this.$refs.playModeTabMenu.openMenu(event);
    },
    changePlaylistFilter(type) {
      this.updateData({ key: 'libraryPlaylistFilter', value: type });
    },
    selectUploadFiles() {
      this.$refs.cloudDiskUploadInput.click();
    },
    async uploadSongToCloudDisk(event) {
      const file = event.target.files?.[0];
      if (!file) return;
      try {
        const result = await uploadSong(file);
        if (result.code !== 200) throw new Error(result.message || '上传失败');
        this.$store.commit('updateLikedXXX', {
          name: 'cloudDisk',
          data: [result.privateCloud, ...this.liked.cloudDisk],
        });
        this.showToast('歌曲已上传到云盘');
      } catch (error) {
        this.showToast(error.message || '上传失败');
      } finally {
        event.target.value = '';
      }
    },
  },
};
</script>

<style lang="scss" scoped>
.library-page {
  color: var(--color-text);
}

h1 {
  display: flex;
  align-items: center;
  margin: 24px 0 44px;
  font-size: 42px;

  .avatar {
    width: 52px;
    height: 52px;
    margin-right: 14px;
    border-radius: 50%;
    object-fit: cover;
  }
}

.section-one {
  display: grid;
  grid-template-columns: minmax(280px, 3fr) minmax(0, 7fr);
  gap: 36px;
}

.liked-songs {
  display: flex;
  min-height: 270px;
  padding: 24px 28px;
  overflow: hidden;
  flex-direction: column;
  box-sizing: border-box;
  color: var(--color-primary);
  background: var(--color-primary-bg);
  border-radius: 16px;
  cursor: pointer;
  transition: transform 0.25s, box-shadow 0.25s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 16px 34px -22px rgba(0, 0, 0, 0.45);
  }

  .top {
    flex: 1;
    font-size: 14px;
    line-height: 1.65;
    opacity: 0.88;

    p {
      margin: 0;
    }
  }

  .lyric-placeholder {
    max-width: 220px;
    font-size: 20px;
    font-weight: 600;
    opacity: 0.24;
  }

  .bottom {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .title {
      font-size: 24px;
      font-weight: 700;
    }

    .sub-title {
      margin-top: 3px;
      font-size: 15px;
    }

    button {
      display: grid;
      width: 48px;
      height: 48px;
      place-items: center;
      color: var(--color-primary-bg);
      background: var(--color-primary-gradient);
      border-radius: 50%;
      box-shadow: 0 6px 14px -7px rgba(0, 0, 0, 0.55);
      transition: transform 0.2s;

      &:hover {
        transform: scale(1.06);
      }

      .svg-icon {
        width: 17px;
        height: 17px;
        margin-left: 3px;
      }
    }
  }
}

.songs {
  min-width: 0;
  overflow: hidden;
}

.section-two {
  min-height: calc(100vh - 182px);
  margin-top: 54px;
}

.tabs-row,
.tabs {
  display: flex;
  align-items: center;
}

.tabs-row {
  justify-content: space-between;
  margin-bottom: 24px;
}

.tabs {
  flex-wrap: wrap;
  gap: 6px;
}

.tab {
  padding: 8px 14px;
  color: var(--color-text);
  font-size: 18px;
  font-weight: 600;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  opacity: 0.62;
  transition: 0.2s;

  &:hover,
  &.active {
    background: var(--color-secondary-bg);
    opacity: 0.9;
  }
}

.tab.dropdown {
  display: flex;
  align-items: center;
  padding: 0;

  .text {
    padding: 8px 3px 8px 14px;
  }

  .icon {
    display: flex;
    padding: 10px 9px 10px 3px;

    .svg-icon {
      width: 16px;
      height: 16px;
    }
  }
}

.tab-button {
  display: flex;
  align-items: center;
  padding: 9px 14px;
  color: var(--color-text);
  font-weight: 600;
  white-space: nowrap;
  border-radius: 8px;
  opacity: 0.66;
  transition: 0.2s;

  .svg-icon {
    width: 14px;
    height: 14px;
    margin-right: 8px;
  }

  &:hover {
    background: var(--color-secondary-bg);
    opacity: 1;
  }
}

.history-switch {
  display: flex;
  gap: 6px;
  margin-bottom: 14px;

  button {
    padding: 7px 11px;
    color: var(--color-text);
    font-weight: 600;
    border-radius: 8px;
    opacity: 0.62;

    &.selected {
      background: var(--color-secondary-bg);
      opacity: 1;
    }
  }
}

.empty-state {
  display: grid;
  min-height: 180px;
  place-items: center;
  color: var(--color-text);
  background: var(--color-secondary-bg);
  border-radius: 14px;
  opacity: 0.58;
}

@media (max-width: 1050px) {
  .section-one {
    grid-template-columns: 1fr;
  }

  .liked-songs {
    min-height: 240px;
  }
}

@media (max-width: 700px) {
  h1 {
    margin-bottom: 28px;
    font-size: 32px;
  }

  .tabs-row {
    align-items: flex-start;
  }

  .tab-button {
    margin-top: 2px;
  }
}
</style>
