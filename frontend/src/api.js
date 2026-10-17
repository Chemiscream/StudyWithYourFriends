// 请求封装：同源 API（dev 由 Vite 代理，prod 由 Spring Boot 直接服务）
export const API = "";

export function getToken() { return localStorage.getItem("studylog_token") || ""; }
export function setToken(t) { t ? localStorage.setItem("studylog_token", t) : localStorage.removeItem("studylog_token"); }

export function authHeaders() {
  const token = getToken();
  return token ? { Authorization: "Bearer " + token } : {};
}

// 普通请求：总是返回解析后的 JSON（后端所有接口都返回 JSON）
export async function request(path, opts = {}) {
  const headers = Object.assign({ "Content-Type": "application/json" }, authHeaders(), opts.headers || {});
  const res = await fetch(API + path, Object.assign({ headers }, opts));
  return await res.json();
}
