<template>
  <div>
    <div class="page-heading">
      <div>
        <p class="eyebrow">FROM REQUEST TO RESOLUTION</p>
        <h1 tabindex="-1">Work orders</h1>
        <p class="muted">
          {{
            session.user?.role === "ADMIN"
              ? "Every maintenance task, with a clear next step."
              : session.user?.role === "TECHNICIAN"
                ? "Your assigned maintenance work."
                : "Follow the maintenance requests you submitted."
          }}
        </p>
      </div>
      <RouterLink
        v-if="session.user?.role !== 'TECHNICIAN'"
        class="button"
        to="/work-orders/new"
        >＋ Create request</RouterLink
      >
    </div>
    <p v-if="assetId" class="notice">
      Showing orders for asset #{{ assetId }}.
      <RouterLink to="/work-orders">Clear asset filter</RouterLink>
    </p>
    <ErrorPanel :error="error" />
    <section class="card">
      <form class="filters" @submit.prevent="load(0)">
        <div class="search-field">
          <label for="search">Search work orders</label
          ><input id="search" v-model="search" placeholder="Search by title" />
        </div>
        <div>
          <label for="status">Status</label
          ><select id="status" v-model="status">
            <option value="">All statuses</option>
            <option v-for="s in statuses" :key="s" :value="s">
              {{ label(s) }}
            </option>
          </select>
        </div>
        <div>
          <label for="priority">Priority</label
          ><select id="priority" v-model="priority">
            <option value="">All priorities</option>
            <option>LOW</option>
            <option>MEDIUM</option>
            <option>HIGH</option>
          </select>
        </div>
        <div>
          <label for="sort">Sort by</label
          ><select id="sort" v-model="sort">
            <option value="createdAt">Created date</option>
            <option value="dueDate">Due date</option>
            <option value="title">Title</option>
            <option value="updatedAt">Last updated</option>
          </select>
        </div>
        <div>
          <label for="direction">Direction</label
          ><select id="direction" v-model="direction">
            <option value="desc">Descending</option>
            <option value="asc">Ascending</option>
          </select>
        </div>
        <button class="button secondary" :disabled="loading">Apply</button>
      </form>
      <p v-if="loading" class="empty" role="status">Loading work orders…</p>
      <div v-else-if="page.content.length" class="table-wrap">
        <table>
          <caption class="sr-only">
            Visible work orders
          </caption>
          <thead>
            <tr>
              <th scope="col">Work order</th>
              <th scope="col">Asset</th>
              <th scope="col">Priority</th>
              <th scope="col">Status</th>
              <th scope="col">Assigned to</th>
              <th scope="col">Due date (UTC)</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in page.content" :key="order.id">
              <td>
                <RouterLink :to="'/work-orders/' + order.id"
                  ><strong>{{ order.title }}</strong
                  ><small>WO-{{ order.id }}</small></RouterLink
                >
              </td>
              <td>{{ order.assetCode }}</td>
              <td><StatusBadge :value="order.priority" /></td>
              <td><StatusBadge :value="order.status" /></td>
              <td>{{ order.technician?.displayName || "Unassigned" }}</td>
              <td>{{ order.dueDate || "No due date" }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty">
        <h3>No work orders here yet</h3>
        <p>
          Try adjusting your filters. Only records permitted for your role
          appear here.
        </p>
      </div>
      <PageControls v-bind="page" :busy="loading" @change="load" />
    </section>
  </div>
</template>
<script>
import { api, query, session, label } from "../api";
export default {
  data() {
    return {
      search: "",
      status: "",
      priority: "",
      sort: "createdAt",
      direction: "desc",
      assetId: this.$route.query.assetId || "",
      statuses: ["OPEN", "IN_PROGRESS", "COMPLETED", "CANCELLED"],
      page: { content: [], number: 0, totalPages: 0, totalElements: 0 },
      loading: false,
      error: null,
      session,
    };
  },
  mounted() {
    this.load(0);
  },
  methods: {
    label,
    async load(number) {
      if (this.loading) return;
      this.loading = true;
      this.error = null;
      try {
        this.page = await api(
          "/work-orders?" +
            query({
              search: this.search,
              status: this.status,
              priority: this.priority,
              assetId: this.assetId,
              sort: this.sort,
              direction: this.direction,
              page: number,
            }),
        );
      } catch (error) {
        this.error = error;
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>
