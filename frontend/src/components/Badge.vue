<template>
  <span class="badge" :class="value?.toLowerCase()"
    ><span class="dot" aria-hidden="true"></span>{{ label(value) }}</span
  >
</template>
<script>
import { label } from "../api";
export default { props: { value: String }, methods: { label } };
</script>
