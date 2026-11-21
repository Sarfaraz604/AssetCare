<template>
  <div>
    <div class="page-heading">
      <div>
        <p class="eyebrow">KNOW WHAT YOU CARE FOR</p>
        <h1 tabindex="-1">Asset catalogue</h1>
        <p class="muted">The equipment behind your everyday operations.</p>
      </div>
      <RouterLink v-if="isAdmin" class="button" to="/assets/new"
        >＋ Add asset</RouterLink
      >
    </div>
    <ErrorPanel :error="error" />
    <section class="card">
      <form class="filters" @submit.prevent="load(0)">
        <div class="search-field">
          <label for="search">Search assets</label
          ><input
            id="search"
            v-model="search"
            placeholder="Name, code or location"
          />
        </div>
        <div>
          <label for="status">Status</label
          ><select id="status" v-model="status">
            <option value="">All statuses</option>
            <option v-for="value in statuses" :key="value" :value="value">
              {{ label(value) }}
            </option>
          </select>
        </div>
        <div>
          <label for="category">Category</label
          ><input
            id="category"
            v-model="category"
            placeholder="Exact category"
          />
        </div>
        <div>
          <label for="sort">Sort by</label
          ><select id="sort" v-model="sort">
            <option value="code">Asset code</option>
            <option value="name">Name</option>
            <option value="updatedAt">Last updated</option>
          </select>
        </div>
        <div>
          <label for="direction">Direction</label
          ><select id="direction" v-model="direction">
            <option value="asc">Ascending</option>
            <option value="desc">Descending</option>
          </select>
        </div>
        <button class="button secondary" :disabled="loading">Apply</button>
      </form>
      <p v-if="loading" class="empty" role="status">Loading assets…</p>
      <div v-else-if="page.content.length" class="table-wrap">
        <table>
          <caption class="sr-only">
            Asset catalogue
          </caption>
          <thead>
            <tr>
              <th scope="col">Asset</th>
              <th scope="col">Category</th>
              <th scope="col">Location</th>
              <th scope="col">Status</th>
              <th scope="col"><span class="sr-only">Details</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="asset in page.content" :key="asset.id">
              <td>
                <RouterLink :to="'/assets/' + asset.id"
                  ><strong>{{ asset.name }}</strong
                  ><small>{{ asset.code }}</small></RouterLink
                >
              </td>
              <td>{{ asset.category }}</td>
              <td>{{ asset.location }}</td>
              <td><StatusBadge :value="asset.status" /></td>
              <td>
                <RouterLink
                  :to="'/assets/' + asset.id"
                  :aria-label="'View ' + asset.name"
                  >↗</RouterLink
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty">
        <h3>No assets found</h3>
        <p>Try a different search or add your first asset.</p>
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
      category: "",
      sort: "code",
      direction: "asc",
      statuses: ["ACTIVE", "UNDER_MAINTENANCE", "RETIRED"],
      page: { content: [], number: 0, totalPages: 0, totalElements: 0 },
      loading: false,
      error: null,
      session,
    };
  },
  computed: {
    isAdmin() {
      return this.session.user?.role === "ADMIN";
    },
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
          "/assets?" +
            query({
              search: this.search,
              status: this.status,
              category: this.category,
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
