import { reactive } from "vue";
export const session = reactive({ user: null, ready: false });
let csrf = null;
export async function refreshCsrf() {
  const response = await fetch("/api/auth/csrf", {
    credentials: "same-origin",
  });
  if (!response.ok)
    throw new Error("Unable to establish a secure session. Please retry.");
  csrf = await response.json();
}
export async function api(path, options = {}) {
  const method = options.method || "GET";
  const headers = { ...options.headers };
  if (method !== "GET") {
    if (!csrf) await refreshCsrf();
    headers[csrf.headerName] = csrf.token;
  }
  let body = options.body;
  if (body && !(body instanceof URLSearchParams)) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(body);
  }
  let response;
  try {
    response = await fetch("/api" + path, {
      ...options,
      method,
      headers,
      body,
      credentials: "same-origin",
    });
  } catch {
    throw new Error("Cannot reach AssetCare. Check your connection and retry.");
  }
  if (!response.ok) {
    let data;
    try {
      data = await response.json();
    } catch {
      data = { message: "The server could not complete this request." };
    }
    const error = new Error(data.message);
    error.code = data.code;
    error.fields = data.fieldErrors || {};
    error.requestId = data.requestId;
    error.status = response.status;
    if (response.status === 401) session.user = null;
    // Do not replay a mutation automatically after a session or token expires.
    if (response.status === 403 || response.status === 401) csrf = null;
    throw error;
  }
  return response.status === 204 ? null : response.json();
}
export async function loadSession() {
  if (session.ready) return;
  try {
    session.user = await api("/auth/me");
  } catch (error) {
    if (error.status !== 401) throw error;
  }
  session.ready = true;
}
export async function login(username, password) {
  await refreshCsrf();
  await api("/auth/login", {
    method: "POST",
    body: new URLSearchParams({ username, password }),
  });
  await refreshCsrf();
  session.user = await api("/auth/me");
  session.ready = true;
}
export async function logout() {
  await api("/auth/logout", { method: "POST" });
  session.user = null;
  csrf = null;
}
export function query(values) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values))
    if (value !== "" && value !== null && value !== undefined)
      params.set(key, value);
  return params.toString();
}
export function label(value) {
  return value
    ? value
        .toLowerCase()
        .replaceAll("_", " ")
        .replace(/^./, (letter) => letter.toUpperCase())
    : "Unassigned";
}
export function dateTime(value) {
  return value ? new Date(value).toLocaleString() : "—";
}
