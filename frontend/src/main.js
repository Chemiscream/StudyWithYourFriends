import { createApp } from "vue";
import App from "./App.vue";
import "./style.css";
// 皮肤预设 B（Aero / Y2K）：只在 <html data-skin="aero"> 时生效，必须排在 style.css 之后
import "./skin.css";

createApp(App).mount("#app");
