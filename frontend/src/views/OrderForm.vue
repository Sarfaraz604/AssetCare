<template>
  <div class="form-page">
    <RouterLink class="back-link" to="/work-orders">← Work orders</RouterLink>
    <p class="eyebrow">LET’S TAKE CARE OF IT</p>
    <h1 tabindex="-1">Request maintenance</h1>
    <p class="muted">
      Tell the team what needs attention. New requests start as open.
    </p>
    <ErrorPanel :error="error" />
    <form class="card form-card" @submit.prevent="submit">
      <label for="asset-search">Find an asset</label>
      <div class="inline-control">
        <input
          id="asset-search"
          v-model="assetSearch"
          placeholder="Search name or code"
        /><button
          class="button secondary"
          type="button"
          :disabled="searching"
          @click="findAssets"
        >
          {{ searching ? "Finding…" : "Find" }}
        </button>
      </div>
      <label for="asset">Asset</label
      ><select id="asset" v-model="form.assetId" required :disabled="searching">
        <option value="">Choose an asset</option>
        <option
          v-for="asset in assets"
          :key="asset.id"
          :value="asset.id"
          :disabled="asset.status === 'RETIRED'"
        >
          {{ asset.code }} · {{ asset.name
          }}{{ asset.status === "RETIRED" ? " (retired)" : "" }}
        </option>
      </select>
      <p class="help" role="status">
        {{
          assets.length
            ? "Showing up to 100 matches. Refine your search if needed."
            : "No assets found. Try a different search."
        }}
      </p>
      <label for="title">Title</label
      ><input
        id="title"
        v-model="form.title"
        required
        maxlength="160"
        placeholder="A short summary of the issue"
      /><label for="description">Description</label
      ><textarea
        id="description"
        v-model="form.description"
        required
        maxlength="4000"
        rows="5"
        placeholder="What happened? Where should the technician look?"
      ></textarea>
      <div class="form-grid">
        <div>
          <label for="priority">Priority</label
          ><select id="priority" v-model="form.priority">
            <option value="LOW">Low</option>
            <option value="MEDIUM">Medium</option>
            <option value="HIGH">High</option>
          </select>
        </div>
        <div>
          <label for="due">Due date <span class="muted">· optional</span></label
          ><input id="due" v-model="form.dueDate" type="date" />
        </div>
      </div>
      <p class="help">
        Dates use the UTC calendar. An active order becomes overdue the day
        after its due date.
      </p>
      <div class="form-actions">
        <RouterLink class="button secondary" to="/work-orders"
          >Cancel</RouterLink
        ><button class="button" :disabled="busy || searching">
          {{ busy ? "Submitting…" : "Submit request" }}
        </button>
      </div>
    </form>
  </div>
</template>
<script>
import { api, query } from "../api";
export default {
  data() {
    return {
      form: {
        assetId: "",
        title: "",
        description: "",
        priority: "MEDIUM",
        dueDate: "",
      },
      assetSearch: "",
      assets: [],
      busy: false,
      searching: false,
      error: null,
    };
  },
  async mounted() {
    await this.findAssets();
    if (this.$route.query.assetId) {
      try {
        const asset = await api("/assets/" + this.$route.query.assetId);
        if (!this.assets.some((item) => item.id === asset.id))
          this.assets.unshift(asset);
        this.form.assetId = asset.id;
      } catch (error) {
        this.error = error;
      }
    }
  },
  methods: {
    async findAssets() {
      if (this.searching) return;
      this.searching = true;
      this.error = null;
      try {
        const result = await api(
          "/assets?" + query({ search: this.assetSearch, size: 100 }),
        );
        const selected = this.assets.find(
          (item) => item.id === this.form.assetId,
        );
        this.assets = result.content;
        if (selected && !this.assets.some((item) => item.id === selected.id))
          this.assets.unshift(selected);
      } catch (error) {
        this.error = error;
      } finally {
        this.searching = false;
      }
    },
    async submit() {
      if (this.busy) return;
      this.busy = true;
      this.error = null;
      try {
        const result = await api("/work-orders", {
          method: "POST",
          body: { ...this.form, dueDate: this.form.dueDate || null },
        });
        this.$router.push("/work-orders/" + result.id + "?created=1");
      } catch (error) {
        this.error = error;
      } finally {
        this.busy = false;
      }
    },
  },
};
</script>
