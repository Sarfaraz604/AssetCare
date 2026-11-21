<template>
  <nav class="pagination" aria-label="Pagination">
    <span
      >{{ totalElements }} records · Page {{ number + 1 }} of
      {{ Math.max(totalPages, 1) }}</span
    >
    <div>
      <button
        class="button secondary small"
        :disabled="busy || number === 0"
        @click="$emit('change', number - 1)"
      >
        Previous</button
      ><button
        class="button secondary small"
        :disabled="busy || number + 1 >= totalPages"
        @click="$emit('change', number + 1)"
      >
        Next
      </button>
    </div>
  </nav>
</template>
<script>
export default {
  props: {
    number: Number,
    totalPages: Number,
    totalElements: Number,
    busy: Boolean,
  },
  emits: ["change"],
};
</script>
