import { createRouter, createWebHashHistory } from "vue-router";
import { loadSession, session } from "./api";
import Login from "./views/Login.vue";
import Dashboard from "./views/Dashboard.vue";
import Assets from "./views/Assets.vue";
import AssetDetail from "./views/AssetDetail.vue";
import AssetForm from "./views/AssetForm.vue";
import Orders from "./views/Orders.vue";
import OrderForm from "./views/OrderForm.vue";
import OrderDetail from "./views/OrderDetail.vue";
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: "/login", component: Login },
    { path: "/", component: Dashboard },
    { path: "/assets", component: Assets },
    { path: "/assets/new", component: AssetForm, meta: { admin: true } },
    { path: "/assets/:id/edit", component: AssetForm, meta: { admin: true } },
    { path: "/assets/:id", component: AssetDetail },
    { path: "/work-orders", component: Orders },
    { path: "/work-orders/new", component: OrderForm },
    { path: "/work-orders/:id", component: OrderDetail },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
  scrollBehavior() {
    return { top: 0 };
  },
});
router.beforeEach(async (to) => {
  try {
    await loadSession();
  } catch {
    return to.path === "/login" ? true : "/login";
  }
  if (!session.user && to.path !== "/login") return "/login";
  if (session.user && to.path === "/login") return "/";
  if (to.meta.admin && session.user?.role !== "ADMIN") return "/assets";
  if (to.path === "/work-orders/new" && session.user?.role === "TECHNICIAN")
    return "/work-orders";
});
router.afterEach(() => {
  setTimeout(() => document.querySelector("h1")?.focus(), 0);
});
export default router;
