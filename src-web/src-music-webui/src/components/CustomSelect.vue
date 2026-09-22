<template>
  <div
    ref="root"
    class="custom-select"
    :class="{ open, disabled }"
    @keydown="handleKeydown"
  >
    <button
      class="custom-select-trigger"
      type="button"
      role="combobox"
      :aria-label="ariaLabel"
      :aria-expanded="String(open)"
      aria-haspopup="listbox"
      :disabled="disabled"
      @click="toggle"
    >
      <span>{{ selectedOption.label }}</span>
      <span class="chevron" aria-hidden="true"></span>
    </button>

    <transition name="select-menu">
      <div v-if="open" class="custom-select-menu" role="listbox">
        <button
          v-for="(option, index) in options"
          :key="optionKey(option, index)"
          class="custom-select-option"
          :class="{
            selected: isSelected(option),
            active: index === activeIndex,
          }"
          type="button"
          role="option"
          :aria-selected="String(isSelected(option))"
          @mouseenter="activeIndex = index"
          @mousedown.prevent
          @click="select(option)"
        >
          <span>{{ option.label }}</span>
          <span v-if="isSelected(option)" class="check" aria-hidden="true">
            ✓
          </span>
        </button>
      </div>
    </transition>
  </div>
</template>

<script>
export default {
  name: 'CustomSelect',
  model: { prop: 'value', event: 'input' },
  props: {
    value: {
      type: [String, Number, Boolean],
      default: '',
    },
    options: {
      type: Array,
      default: () => [],
    },
    disabled: Boolean,
    ariaLabel: {
      type: String,
      default: '选择',
    },
  },
  data() {
    return {
      open: false,
      activeIndex: -1,
    };
  },
  computed: {
    selectedIndex() {
      return this.options.findIndex(option =>
        this.valuesEqual(option.value, this.value)
      );
    },
    selectedOption() {
      return (
        this.options[this.selectedIndex] ||
        this.options[0] || { label: '', value: '' }
      );
    },
  },
  watch: {
    disabled(value) {
      if (value) this.close();
    },
  },
  mounted() {
    document.addEventListener('mousedown', this.handleOutside);
  },
  beforeDestroy() {
    document.removeEventListener('mousedown', this.handleOutside);
  },
  methods: {
    valuesEqual(left, right) {
      if (left === right) return true;
      if (typeof left === 'boolean' || typeof right === 'boolean') return false;
      return String(left) === String(right);
    },
    optionKey(option, index) {
      return `${typeof option.value}:${String(option.value)}:${index}`;
    },
    isSelected(option) {
      return this.valuesEqual(option.value, this.value);
    },
    toggle() {
      if (this.disabled) return;
      this.open ? this.close() : this.openMenu();
    },
    openMenu() {
      this.open = true;
      this.activeIndex = Math.max(this.selectedIndex, 0);
    },
    close() {
      this.open = false;
      this.activeIndex = -1;
    },
    select(option) {
      if (this.disabled) return;
      this.$emit('input', option.value);
      this.$emit('change', option.value);
      this.close();
      this.$nextTick(() => this.$refs.root?.querySelector('button')?.focus());
    },
    moveActive(step) {
      if (!this.options.length) return;
      if (!this.open) this.openMenu();
      this.activeIndex =
        (this.activeIndex + step + this.options.length) % this.options.length;
    },
    handleKeydown(event) {
      if (this.disabled) return;
      if (event.key === 'ArrowDown') {
        event.preventDefault();
        this.moveActive(1);
      } else if (event.key === 'ArrowUp') {
        event.preventDefault();
        this.moveActive(-1);
      } else if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        if (!this.open) {
          this.openMenu();
        } else if (this.options[this.activeIndex]) {
          this.select(this.options[this.activeIndex]);
        }
      } else if (event.key === 'Escape' || event.key === 'Tab') {
        this.close();
      } else if (event.key === 'Home') {
        event.preventDefault();
        this.openMenu();
        this.activeIndex = 0;
      } else if (event.key === 'End') {
        event.preventDefault();
        this.openMenu();
        this.activeIndex = this.options.length - 1;
      }
    },
    handleOutside(event) {
      if (this.open && !this.$refs.root?.contains(event.target)) this.close();
    },
  },
};
</script>

<style lang="scss" scoped>
.custom-select {
  position: relative;
  width: 240px;
  color: var(--color-text);
  font-size: 14px;
  font-weight: 600;
  text-align: left;
  z-index: 1;

  &.open {
    z-index: 80;
  }
}

.custom-select-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 38px;
  padding: 9px 12px;
  color: inherit;
  font: inherit;
  text-align: left;
  background: var(--color-secondary-bg);
  border: 1px solid transparent;
  border-radius: 8px;
  transition: color 0.18s, background 0.18s, border-color 0.18s;

  &:hover {
    background: var(--color-secondary-bg-for-transparent);
  }

  &:focus-visible,
  .open & {
    color: var(--color-primary);
    background: var(--color-primary-bg-for-transparent);
    border-color: var(--color-primary);
    outline: none;
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.4;
  }
}

.chevron {
  width: 7px;
  height: 7px;
  margin: -4px 2px 0 12px;
  border-right: 2px solid currentColor;
  border-bottom: 2px solid currentColor;
  transform: rotate(45deg);
  transition: transform 0.18s;

  .open & {
    margin-top: 3px;
    transform: rotate(225deg);
  }
}

.custom-select-menu {
  position: absolute;
  top: calc(100% + 7px);
  right: 0;
  left: 0;
  max-height: 272px;
  padding: 6px;
  overflow: auto;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(0, 0, 0, 0.07);
  border-radius: 10px;
  box-shadow: 0 14px 34px -14px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(18px) saturate(140%);
}

[data-theme='dark'] .custom-select-menu {
  background: rgba(42, 42, 42, 0.94);
  border-color: rgba(255, 255, 255, 0.09);
  box-shadow: 0 16px 38px -12px rgba(0, 0, 0, 0.72);
}

.custom-select-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 36px;
  padding: 8px 10px;
  color: var(--color-text);
  font: inherit;
  text-align: left;
  border-radius: 7px;
  opacity: 0.76;

  &:hover,
  &.active {
    color: var(--color-text);
    background: var(--color-secondary-bg-for-transparent);
    opacity: 1;
  }

  &.selected {
    color: var(--color-primary);
    background: var(--color-primary-bg-for-transparent);
    opacity: 1;
  }
}

.check {
  margin-left: 12px;
  font-size: 13px;
}

.disabled {
  pointer-events: none;
}

.select-menu-enter-active,
.select-menu-leave-active {
  transition: opacity 0.14s, transform 0.14s;
  transform-origin: top;
}

.select-menu-enter,
.select-menu-leave-to {
  opacity: 0;
  transform: translateY(-5px) scale(0.98);
}
</style>
