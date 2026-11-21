<template>
  <a class="skip" href="#main">Skip to content</a>
  <div :class="{ workspace: user }">
    <aside v-if="user" class="sidebar">
      <RouterLink class="brand" to="/"
        ><span class="brand-mark" aria-hidden="true">A</span
        ><span>AssetCare<small>MAINTENANCE WORKSPACE</small></span></RouterLink
      >
      <div class="workspace-label">WORKSPACE</div>
      <nav aria-label="Main navigation">
        <RouterLink to="/" exact-active-class="selected"
          ><span aria-hidden="true">&#x25eb;</span>Overview</RouterLink
        ><RouterLink to="/assets" active-class="selected"
          ><span aria-hidden="true">&#x25a6;</span>Assets</RouterLink
        ><RouterLink to="/work-orders" active-class="selected"
          ><span aria-hidden="true">&#x2637;</span>Work orders</RouterLink
        >
      </nav>
      <div class="sidebar-note">
        <span class="live-dot" aria-hidden="true"></span>Care today. Reliability
        tomorrow.
        <p>A clear view of the assets<br />that keep things moving.</p>
      </div>
      <div class="profile">
        <span class="avatar">{{ user.displayName.charAt(0) }}</span>
        <div>
          <strong>{{ user.displayName }}</strong
          ><small>{{ label(user.role) }}</small>
        </div>
        <button
          aria-label="Sign out"
          title="Sign out"
          :disabled="signingOut"
          @click="signOut"
        >
          &#x21aa;
        </button>
      </div>
    </aside>
    <div :class="user ? 'main-shell' : 'login-shell'">
      <header v-if="user" class="topbar">
        <span>Operations <span class="divider">/</span> Maintenance</span
        ><span class="environment">{{ label(user.role) }} workspace</span>
      </header>
      <main id="main">
        <ErrorPanel :error="error" /><RouterView :key="$route.fullPath" />
      </main>
      <footer v-if="user">
        AssetCare <span>Built for better upkeep.</span>
      </footer>
    </div>
  </div>
</template>
<script>
import { session, logout, label } from "./api";
export default {
  data() {
    return { session, signingOut: false, error: null };
  },
  computed: {
    user() {
      return this.session.user;
    },
  },
  methods: {
    label,
    async signOut() {
      this.signingOut = true;
      this.error = null;
      try {
        await logout();
        this.$router.push("/login");
      } catch (error) {
        this.error = error;
      } finally {
        this.signingOut = false;
      }
    },
  },
};
</script>
