<template>
  <div class="login-layout">
    <section class="login-story">
      <RouterLink class="brand" to="/"
        ><span class="brand-mark">A</span>AssetCare</RouterLink
      >
      <div>
        <p class="eyebrow">A LITTLE CARE GOES A LONG WAY</p>
        <h2>Keep your assets.<br />Keep your momentum.</h2>
        <p>
          One workspace for the equipment you rely on<br />and the people who
          keep it running.
        </p>
        <div class="line-art" aria-hidden="true">
          <div></div>
          <div></div>
          <div></div>
        </div>
      </div>
      <small>ASSET MAINTENANCE & WORK ORDER MANAGER</small>
    </section>
    <section class="login-form">
      <p class="eyebrow">YOUR MAINTENANCE WORKSPACE</p>
      <h1 tabindex="-1">Welcome back.</h1>
      <p class="muted">Sign in to see what needs your attention.</p>
      <ErrorPanel :error="error" />
      <form @submit.prevent="submit">
        <label for="username">Username</label
        ><input
          id="username"
          v-model="username"
          autocomplete="username"
          required
          maxlength="80"
        /><label for="password">Password</label
        ><input
          id="password"
          v-model="password"
          type="password"
          autocomplete="current-password"
          required
        /><button class="button full" :disabled="busy">
          {{ busy ? "Signing in…" : "Sign in →" }}
        </button>
      </form>
      <p class="help">
        Use an account provisioned by your administrator.<br />Local demo
        accounts require the development seed setup.
      </p>
    </section>
  </div>
</template>
<script>
import { login } from "../api";
export default {
  data() {
    return { username: "", password: "", busy: false, error: null };
  },
  methods: {
    async submit() {
      if (this.busy) return;
      this.busy = true;
      this.error = null;
      try {
        await login(this.username, this.password);
        this.password = "";
        this.$router.push("/");
      } catch (error) {
        this.error = error;
      } finally {
        this.busy = false;
      }
    },
  },
};
</script>
