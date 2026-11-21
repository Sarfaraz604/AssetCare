<template>
  <div v-if="error" class="notice error" role="alert">
    <strong>{{ error.message }}</strong>
    <ul v-if="Object.keys(error.fields || {}).length">
      <li v-for="(message, field) in error.fields" :key="field">
        {{ field }}: {{ message }}
      </li>
    </ul>
    <p v-if="error.code === 'STALE_UPDATE'">
      Your input has been kept. Open the record in another tab to compare, then
      reload before retrying.
    </p>
    <p v-if="error.status === 401">
      <RouterLink to="/login">Sign in again</RouterLink>
    </p>
    <small v-if="error.requestId">Request ID: {{ error.requestId }}</small>
  </div>
</template>
<script>
export default { props: { error: Object } };
</script>
