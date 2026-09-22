<template>
  <Modal
    :show="show"
    :close="close"
    title="新建歌单"
    width="380px"
    min-width="min(380px, calc(100vw - 48px))"
    class="new-playlist-modal"
  >
    <template slot="default">
      <input
        ref="title"
        v-model.trim="title"
        class="title-input"
        type="text"
        placeholder="歌单标题"
        maxlength="40"
        @keyup.enter="submit"
      />
      <label class="privacy">
        <input v-model="privatePlaylist" type="checkbox" />
        <span>设置为隐私歌单</span>
      </label>
    </template>
    <template slot="footer">
      <button class="primary block" :disabled="submitting" @click="submit">
        {{ submitting ? '正在创建…' : '创建' }}
      </button>
    </template>
  </Modal>
</template>

<script>
import { mapActions, mapMutations, mapState } from 'vuex';
import { createPlaylist } from '@/api/playlist';
import Modal from '@/components/Modal.vue';

export default {
  name: 'ModalNewPlaylist',
  components: { Modal },
  data() {
    return {
      title: '',
      privatePlaylist: false,
      submitting: false,
    };
  },
  computed: {
    ...mapState(['modals']),
    show() {
      return this.modals.newPlaylistModal.show;
    },
  },
  watch: {
    show(value) {
      if (value) this.$nextTick(() => this.$refs.title?.focus());
    },
  },
  methods: {
    ...mapActions(['fetchLikedPlaylist', 'showToast']),
    ...mapMutations(['updateData', 'updateModal']),
    close() {
      this.updateModal({
        modalName: 'newPlaylistModal',
        key: 'show',
        value: false,
      });
      this.title = '';
      this.privatePlaylist = false;
    },
    async submit() {
      if (!this.title || this.submitting) return;
      this.submitting = true;
      try {
        const result = await createPlaylist({
          name: this.title,
          privacy: this.privatePlaylist ? 10 : 0,
        });
        if (result.code !== 200) {
          throw new Error(result.message || '创建歌单失败');
        }
        this.updateData({ key: 'libraryPlaylistFilter', value: 'mine' });
        await this.fetchLikedPlaylist();
        this.close();
        this.showToast('成功创建歌单');
      } catch (error) {
        this.showToast(error.message || '创建歌单失败');
      } finally {
        this.submitting = false;
      }
    },
  },
};
</script>

<style lang="scss" scoped>
.title-input {
  width: 100%;
  box-sizing: border-box;
  padding: 11px 13px;
  color: var(--color-text);
  font-size: 16px;
  font-weight: 600;
  background: var(--color-secondary-bg-for-transparent);
  border: 0;
  border-radius: 8px;
  outline: none;

  &:focus {
    color: var(--color-primary);
    background: var(--color-primary-bg-for-transparent);
  }
}

.privacy {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 14px;
  color: var(--color-text);
  font-size: 13px;
  user-select: none;
  opacity: 0.78;
}

button:disabled {
  cursor: wait;
  opacity: 0.55;
}
</style>
