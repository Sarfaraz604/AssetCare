<template>
  <div>
    <RouterLink class="back-link" to="/work-orders">← Work orders</RouterLink
    ><ErrorPanel :error="error" />
    <p v-if="loading" role="status">Loading work order…</p>
    <template v-if="order"
      ><div class="page-heading">
        <div>
          <p class="eyebrow">WO-{{ order.id }} · {{ order.assetCode }}</p>
          <h1 tabindex="-1">{{ order.title }}</h1>
          <p class="muted">
            Requested by {{ order.requester.displayName }} ·
            {{ dateTime(order.createdAt) }}
          </p>
        </div>
        <StatusBadge :value="order.status" />
      </div>
      <p v-if="success" class="notice success" role="status">{{ success }}</p>
      <div class="detail-grid">
        <div>
          <section class="card detail-card">
            <h2>Request details</h2>
            <p class="preserve-text">{{ order.description }}</p>
            <dl class="details">
              <div>
                <dt>Asset</dt>
                <dd>
                  <RouterLink :to="'/assets/' + order.assetId"
                    >{{ order.assetName }} ↗</RouterLink
                  >
                </dd>
              </div>
              <div>
                <dt>Priority</dt>
                <dd><StatusBadge :value="order.priority" /></dd>
              </div>
              <div>
                <dt>Assigned technician</dt>
                <dd>{{ order.technician?.displayName || "Unassigned" }}</dd>
              </div>
              <div>
                <dt>Due date (UTC)</dt>
                <dd>{{ order.dueDate || "No due date" }}</dd>
              </div>
              <div>
                <dt>Last updated</dt>
                <dd>{{ dateTime(order.updatedAt) }}</dd>
              </div>
              <div v-if="order.completedAt">
                <dt>Completed</dt>
                <dd>{{ dateTime(order.completedAt) }}</dd>
              </div>
            </dl>
            <template v-if="order.completionNotes"
              ><h3>Completion notes</h3>
              <p class="preserve-text">{{ order.completionNotes }}</p></template
            >
          </section>
          <section class="card detail-card">
            <h2>Activity history</h2>
            <p v-if="!history.length" class="muted">No history available.</p>
            <ol class="timeline">
              <li v-for="entry in history" :key="entry.id">
                <strong>{{
                  entry.kind === "STATUS"
                    ? "Status changed"
                    : "Assignment changed"
                }}</strong>
                <p>
                  {{ entry.oldValue ? labelValue(entry.oldValue) + " → " : ""
                  }}{{ labelValue(entry.newValue) }}
                </p>
                <small
                  >{{ entry.actor }} · {{ dateTime(entry.createdAt) }}</small
                >
              </li>
            </ol>
          </section>
        </div>
        <aside>
          <section class="card detail-card">
            <p class="eyebrow">NEXT STEP</p>
            <h2>Manage this order</h2>
            <template v-if="active"
              ><form v-if="isAdmin" @submit.prevent="assign">
                <label for="technician">Assign technician</label
                ><select id="technician" v-model="technicianId">
                  <option :value="null">Unassigned</option>
                  <option
                    v-for="tech in technicians"
                    :key="tech.id"
                    :value="tech.id"
                  >
                    {{ tech.displayName }}
                  </option></select
                ><button class="button full secondary" :disabled="busy">
                  Save assignment
                </button>
              </form>
              <template v-if="assignedToMe"
                ><button
                  v-if="order.status === 'OPEN'"
                  class="button full"
                  :disabled="busy"
                  @click="changeStatus('IN_PROGRESS')"
                >
                  Start work →
                </button>
                <form v-else @submit.prevent="changeStatus('COMPLETED')">
                  <label for="notes">Completion notes</label
                  ><textarea
                    id="notes"
                    v-model="completionNotes"
                    required
                    maxlength="4000"
                    rows="5"
                    placeholder="What did you do to resolve this issue?"
                  ></textarea
                  ><button class="button full" :disabled="busy">
                    Complete work ✓
                  </button>
                </form></template
              ><button
                v-if="isAdmin"
                class="button danger secondary full"
                :disabled="busy"
                @click="cancel"
              >
                Cancel work order
              </button>
              <p v-if="!isAdmin && !assignedToMe" class="muted">
                Your request is with the maintenance team. Status updates will
                appear in the activity history.
              </p>
              <p v-if="isAdmin" class="help">
                Assigned technicians start and complete work. Admins manage
                assignments and cancellations.
              </p></template
            >
            <p v-else class="muted">
              This work order is {{ label(order.status).toLowerCase() }} and
              cannot be changed or reopened.
            </p>
            <p v-if="busy" role="status" class="help">Saving your changes…</p>
          </section>
        </aside>
      </div></template
    >
  </div>
</template>
<script>
import { api, session, label, dateTime } from "../api";
export default {
  data() {
    return {
      order: null,
      history: [],
      technicians: [],
      technicianId: null,
      completionNotes: "",
      busy: false,
      loading: true,
      error: null,
      success:
        this.$route.query.created === "1"
          ? "Maintenance request submitted."
          : "",
      session,
    };
  },
  computed: {
    isAdmin() {
      return this.session.user?.role === "ADMIN";
    },
    active() {
      return ["OPEN", "IN_PROGRESS"].includes(this.order?.status);
    },
    assignedToMe() {
      return (
        this.session.user?.role === "TECHNICIAN" &&
        this.order?.technician?.id === this.session.user.id
      );
    },
  },
  async mounted() {
    try {
      await this.load();
      if (this.isAdmin) this.technicians = await api("/technicians");
    } catch (error) {
      this.error = error;
    } finally {
      this.loading = false;
    }
  },
  methods: {
    label,
    dateTime,
    labelValue(value) {
      return ["OPEN", "IN_PROGRESS", "COMPLETED", "CANCELLED"].includes(value)
        ? label(value)
        : value;
    },
    async load() {
      this.order = await api("/work-orders/" + this.$route.params.id);
      this.technicianId = this.order.technician?.id || null;
      this.history = await api("/work-orders/" + this.order.id + "/history");
    },
    async update(action, body, message) {
      if (this.busy) return;
      this.busy = true;
      this.error = null;
      this.success = "";
      try {
        this.order = await api("/work-orders/" + this.order.id + "/" + action, {
          method: "POST",
          body: { ...body, version: this.order.version },
        });
        this.success = message;
        this.history = await api("/work-orders/" + this.order.id + "/history");
      } catch (error) {
        this.error = error;
      } finally {
        this.busy = false;
      }
    },
    async assign() {
      await this.update(
        "assignment",
        { technicianId: this.technicianId },
        "Assignment updated.",
      );
    },
    async changeStatus(status) {
      await this.update(
        "status",
        { status, completionNotes: this.completionNotes },
        "Work-order status updated.",
      );
    },
    async cancel() {
      if (
        this.busy ||
        !window.confirm("Cancel this work order? This action cannot be undone.")
      )
        return;
      await this.changeStatus("CANCELLED");
    },
  },
};
</script>
