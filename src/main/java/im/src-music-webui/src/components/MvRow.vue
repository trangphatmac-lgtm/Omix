<template>
  <div class="mv-row">
    <article v-for="mv in mvs" :key="getID(mv)" class="mv">
      <a
        class="cover"
        :href="`https://music.163.com/#/mv?id=${getID(mv)}`"
        target="_blank"
        rel="noreferrer"
      >
        <img :src="getImage(mv)" loading="lazy" />
        <span class="play"><svg-icon icon-class="play" /></span>
      </a>
      <a
        class="title"
        :href="`https://music.163.com/#/mv?id=${getID(mv)}`"
        target="_blank"
        rel="noreferrer"
      >
        {{ mv.name || mv.title }}
      </a>
      <div class="artist">{{ mv.artistName || firstCreator(mv) }}</div>
    </article>
  </div>
</template>

<script>
export default {
  name: 'MvRow',
  props: {
    mvs: {
      type: Array,
      default: () => [],
    },
  },
  methods: {
    getID(mv) {
      return mv.id ?? mv.vid;
    },
    getImage(mv) {
      const url = mv.imgurl16v9 || mv.cover || mv.coverUrl || '';
      return `${url.replace(/^http:/, 'https:')}?param=464y260`;
    },
    firstCreator(mv) {
      return mv.creator?.[0]?.userName || '';
    },
  },
};
</script>

<style lang="scss" scoped>
.mv-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 36px 24px;
}

.cover {
  position: relative;
  display: block;

  img {
    display: block;
    width: 100%;
    aspect-ratio: 16 / 9;
    object-fit: cover;
    border-radius: 12px;
    transition: filter 0.25s, transform 0.25s;
  }

  .play {
    position: absolute;
    right: 12px;
    bottom: 10px;
    display: grid;
    place-items: center;
    width: 36px;
    height: 36px;
    color: var(--color-primary-bg);
    background: var(--color-primary-gradient);
    border-radius: 50%;
    opacity: 0;
    transform: translateY(6px);
    transition: 0.25s;

    .svg-icon {
      width: 14px;
      height: 14px;
      margin-left: 2px;
    }
  }

  &:hover {
    img {
      filter: brightness(0.76);
      transform: scale(1.01);
    }

    .play {
      opacity: 1;
      transform: translateY(0);
    }
  }
}

.title {
  display: -webkit-box;
  margin-top: 10px;
  overflow: hidden;
  color: var(--color-text);
  font-size: 16px;
  font-weight: 600;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.artist {
  margin-top: 2px;
  overflow: hidden;
  color: var(--color-text);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
  opacity: 0.68;
}

@media (max-width: 1100px) {
  .mv-row {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 800px) {
  .mv-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .mv-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
