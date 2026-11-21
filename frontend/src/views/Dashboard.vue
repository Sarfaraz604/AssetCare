<template>
  <div>
    <div class="page-heading">
      <div>
        <p class="eyebrow">THE BIG PICTURE</p>
        <h1 tabindex="-1">Maintenance overview</h1>
        <p class="muted">A little visibility. A lot less downtime.</p>
      </div>
      <RouterLink v-if="canRequest" class="button" to="/work-orders/new"
        >＋ Create request</RouterLink
      >
    </div>
    <ErrorPanel :error="error" />
    <p v-if="loading" class="empty" role="status">Loading your overview…</p>
    <template v-else-if="stats"
      ><div class="overview-banner">
        <div><span class="live-dot" aria-hidden="true"></span> {{ scope }}</div>
        <small>Work-order totals reflect your access · Due dates use UTC</small>
      </div>
      <section class="metrics" aria-label="Maintenance totals">
        <article v-for="metric in metrics" :key="metric.label" class="metric">
          <p>
            {{ metric.label }} <span aria-hidden="true">{{ metric.icon }}</span>
          </p>
          <strong :class="metric.color">{{ metric.value }}</strong
          ><small>{{ metric.caption }}</small>
        </article>
      </section>
      <div class="dashboard-grid">
        <section class="card">
          <div class="card-heading">
            <div>
              <p class="eyebrow">KEEP THINGS MOVING</p>
              <h2>Recent work orders</h2>
            </div>
            <RouterLink to="/work-orders">View all ↗</RouterLink>
          </div>
          <div v-if="!stats.recent.length" class="empty">
            <span class="empty-symbol" aria-hidden="true">☷</span>
            <h3>A clear workbench</h3>
            <p>No work orders visible yet.</p>
            <RouterLink v-if="canRequest" to="/work-orders/new"
              >Create your first request →</RouterLink
            >
          </div>
          <div v-else class="recent-list">
            <RouterLink
              v-for="order in stats.recent"
              :key="order.id"
              :to="'/work-orders/' + order.id"
              class="recent-order"
              ><span class="order-icon" aria-hidden="true">↗</span>
              <div>
                <strong>{{ order.title }}</strong
                ><small>{{ order.assetCode }} · WO-{{ order.id }}</small>
              </div>
              <StatusBadge :value="order.status"
            /></RouterLink>
          </div>
        </section>
        <section class="card breakdown">
          <p class="eyebrow">AT A GLANCE</p>
          <h2>Status breakdown</h2>
          <p class="muted">Active work and completed work</p>
          <div class="status-bar" aria-hidden="true">
            <span
              v-for="item in breakdown"
              :key="item.label"
              :class="item.class"
              :style="{ flex: item.value }"
            ></span>
          </div>
          <dl>
            <div v-for="item in breakdown" :key="item.label">
              <dt>
                <span
                  class="legend-dot"
                  :class="item.class"
                  aria-hidden="true"
                ></span
                >{{ item.label }}
              </dt>
              <dd>{{ item.value }}</dd>
            </div>
          </dl>
          <p class="help">
            Cancelled orders remain available in the work-order list.
          </p>
        </section>
      </div>
      <section class="next-step">
        <span aria-hidden="true">▦</span>
        <div>
          <h3>Good maintenance starts with a good inventory.</h3>
          <p>
            Keep asset details current so every request starts in the right
            place.
          </p>
        </div>
        <RouterLink class="button secondary" to="/assets"
          >Explore assets →</RouterLink
        >
      </section></template
    >
  </div>
</template>
<script>
import { api, session } from "../api";
export default {
  data() {
    return { stats: null, loading: true, error: null, session };
  },
  computed: {
    canRequest() {
      return this.session.user?.role !== "TECHNICIAN";
    },
    scope() {
      return {
        ADMIN: "All work orders across your workspace",
        TECHNICIAN: "Work orders assigned to you",
        VIEWER: "Maintenance requests submitted by you",
      }[this.session.user?.role];
    },
    metrics() {
      return [
        {
          label: "Total assets",
          value: this.stats.totalAssets,
          caption: "Across the asset catalogue",
          icon: "▦",
        },
        {
          label: "Open orders",
          value: this.stats.open,
          caption: "Ready for the next step",
          icon: "◷",
        },
        {
          label: "In progress",
          value: this.stats.inProgress,
          caption: "Maintenance underway",
          icon: "↗",
        },
        {
          label: "Overdue",
          value: this.stats.overdue,
          caption: "Past the due date",
          icon: "!",
          color: "danger-text",
        },
        {
          label: "Completed",
          value: this.stats.completed,
          caption: "Work taken care of",
          icon: "✓",
          color: "green-text",
        },
      ];
    },
    breakdown() {
      return [
        { label: "Open", value: this.stats.open, class: "bar-open" },
        {
          label: "In progress",
          value: this.stats.inProgress,
          class: "bar-progress",
        },
        {
          label: "Completed",
          value: this.stats.completed,
          class: "bar-completed",
        },
      ];
    },
  },
  async mounted() {
    try {
      this.stats = await api("/dashboard");
    } catch (error) {
      this.error = error;
    } finally {
      this.loading = false;
    }
  },
};
</script>
