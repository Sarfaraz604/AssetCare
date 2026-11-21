<template>
  <div class="form-page">
    <RouterLink class="back-link" to="/assets">← Asset catalogue</RouterLink>
    <p class="eyebrow">BUILD A BETTER INVENTORY</p>
    <h1 tabindex="-1">{{ editing ? "Edit asset" : "Add an asset" }}</h1>
    <p class="muted">Clear details make maintenance easier for everyone.</p>
    <ErrorPanel :error="error" />
    <p v-if="loading" role="status">Loading asset…</p>
    <form v-else-if="ready" class="card form-card" @submit.prevent="submit">
      <label for="code">Asset code <span class="muted">· unique</span></label
      ><input
        id="code"
        v-model="form.code"
        required
        maxlength="40"
        pattern="[A-Za-z0-9_-]+"
        aria-describedby="code-help"
      /><small id="code-help" class="help"
        >Letters, numbers, hyphens and underscores. Saved in uppercase.</small
      ><label for="name">Name</label
      ><input id="name" v-model="form.name" required maxlength="120" />
      <div class="form-grid">
        <div>
          <label for="category">Category</label
          ><input
            id="category"
            v-model="form.category"
            required
            maxlength="80"
          />
        </div>
        <div>
          <label for="location">Location</label
          ><input
            id="location"
            v-model="form.location"
            required
            maxlength="120"
          />
        </div>
      </div>
      <label for="status">Status</label
      ><select id="status" v-model="form.status">
        <option value="ACTIVE">Active</option>
        <option value="UNDER_MAINTENANCE">Under maintenance</option>
      </select>
      <p class="help">
        Retirement is a separate action on the asset detail page.
      </p>
      <div class="form-actions">
        <RouterLink class="button secondary" to="/assets">Cancel</RouterLink
        ><button class="button" :disabled="busy">
          {{ busy ? "Saving…" : "Save asset" }}
        </button>
      </div>
    </form>
  </div>
</template>
<script>
import { api } from "../api";
export default {
  data() {
    return {
      form: {
        code: "",
        name: "",
        category: "",
        location: "",
        status: "ACTIVE",
      },
      busy: false,
      loading: false,
      ready: false,
      error: null,
    };
  },
  computed: {
    editing() {
      return !!this.$route.params.id;
    },
  },
  async mounted() {
    if (this.editing) {
      this.loading = true;
      try {
        this.form = await api("/assets/" + this.$route.params.id);
        if (this.form.status === "RETIRED")
          throw new Error("Retired assets are read-only.");
        this.ready = true;
      } catch (error) {
        this.error = error;
      } finally {
        this.loading = false;
      }
    } else this.ready = true;
  },
  methods: {
    async submit() {
      if (this.busy) return;
      this.busy = true;
      this.error = null;
      try {
        const result = await api(
          "/assets" + (this.editing ? "/" + this.$route.params.id : ""),
          { method: this.editing ? "PUT" : "POST", body: this.form },
        );
        this.$router.push(
          "/assets/" +
            result.id +
            "?saved=" +
            (this.editing ? "updated" : "created"),
        );
      } catch (error) {
        this.error = error;
      } finally {
        this.busy = false;
      }
    },
  },
};
</script>
