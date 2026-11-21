<template>
  <div>
    <RouterLink class="back-link" to="/assets">← Asset catalogue</RouterLink
    ><ErrorPanel :error="error" />
    <p v-if="loading" role="status">Loading asset…</p>
    <template v-if="asset"
      ><div class="page-heading">
        <div>
          <p class="eyebrow">{{ asset.code }}</p>
          <h1 tabindex="-1">{{ asset.name }}</h1>
          <p class="muted">Asset details & maintenance records</p>
        </div>
        <div class="actions">
          <RouterLink
            v-if="canRequest && asset.status !== 'RETIRED'"
            class="button"
            :to="'/work-orders/new?assetId=' + asset.id"
            >＋ Request maintenance</RouterLink
          ><RouterLink
            v-if="isAdmin && asset.status !== 'RETIRED'"
            class="button secondary"
            :to="'/assets/' + asset.id + '/edit'"
            >Edit asset</RouterLink
          >
        </div>
      </div>
      <p v-if="success" class="notice success" role="status">{{ success }}</p>
      <section class="card detail-card">
        <StatusBadge :value="asset.status" />
        <dl class="details">
          <div>
            <dt>Category</dt>
            <dd>{{ asset.category }}</dd>
          </div>
          <div>
            <dt>Location</dt>
            <dd>{{ asset.location }}</dd>
          </div>
          <div>
            <dt>Created</dt>
            <dd>{{ dateTime(asset.createdAt) }}</dd>
          </div>
          <div>
            <dt>Last updated</dt>
            <dd>{{ dateTime(asset.updatedAt) }}</dd>
          </div>
        </dl>
        <button
          v-if="isAdmin && asset.status !== 'RETIRED'"
          class="button danger secondary"
          :disabled="busy"
          @click="retire"
        >
          {{ busy ? "Retiring…" : "Retire asset" }}
        </button>
      </section>
      <section class="card">
        <div class="card-heading">
          <h2>Maintenance records</h2>
          <RouterLink :to="'/work-orders?assetId=' + asset.id"
            >View history →</RouterLink
          >
        </div>
        <p class="card-description">
          Work orders remain linked to this asset after retirement. Your role
          determines which records you can view.
        </p>
      </section></template
    >
  </div>
</template>
<script>
import { api, session, dateTime } from "../api";
export default {
  data() {
    return {
      asset: null,
      loading: true,
      busy: false,
      error: null,
      success:
        this.$route.query.saved === "created"
          ? "Asset created."
          : this.$route.query.saved === "updated"
            ? "Asset updated."
            : "",
      session,
    };
  },
  computed: {
    isAdmin() {
      return this.session.user?.role === "ADMIN";
    },
    canRequest() {
      return this.session.user?.role !== "TECHNICIAN";
    },
  },
  async mounted() {
    try {
      this.asset = await api("/assets/" + this.$route.params.id);
    } catch (error) {
      this.error = error;
    } finally {
      this.loading = false;
    }
  },
  methods: {
    dateTime,
    async retire() {
      if (
        this.busy ||
        !window.confirm(
          "Retire this asset? Its maintenance history will be preserved. Active work orders must be resolved first.",
        )
      )
        return;
      this.busy = true;
      this.error = null;
      try {
        this.asset = await api("/assets/" + this.asset.id + "/retire", {
          method: "POST",
          body: { version: this.asset.version },
        });
        this.success = "Asset retired. Maintenance history has been preserved.";
      } catch (error) {
        this.error = error;
      } finally {
        this.busy = false;
      }
    },
  },
};
</script>
