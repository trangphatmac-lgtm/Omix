<template>
  <div class="toggle">
    <input
      :id="id"
      type="checkbox"
      :checked="value"
      @change="$emit('change', $event.target.checked)"
    />
    <label :for="id"></label>
  </div>
</template>

<script>
export default {
  name: 'ToggleSwitch',
  model: { prop: 'value', event: 'change' },
  props: {
    id: { type: String, required: true },
    value: Boolean,
  },
};
</script>
