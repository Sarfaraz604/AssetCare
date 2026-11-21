import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import Badge from "./components/Badge.vue";
import ErrorPanel from "./components/ErrorPanel.vue";
import Pager from "./components/Pager.vue";
import "./style.css";
createApp(App)
  .use(router)
  .component("StatusBadge", Badge)
  .component("ErrorPanel", ErrorPanel)
  .component("PageControls", Pager)
  .mount("#app");
