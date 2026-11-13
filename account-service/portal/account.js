const clientId = "account-console";
const protocol = "/auth/realms/squitv/protocol/openid-connect/";
const callback = new URL("/auth/realms/squitv/account/", location.origin);
const transactionKey = "squitv.account.transaction";
const buttons = [...document.querySelectorAll("[data-flow]")];
const status = document.querySelector("#status");
let idToken;
let accessToken;

function randomString() {
  return btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(32))))
    .replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

async function start(flow, replace = false) {
  const state = randomString();
  const verifier = randomString();
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  const challenge = btoa(String.fromCharCode(...new Uint8Array(digest)))
    .replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
  sessionStorage.setItem(transactionKey, JSON.stringify({ state, verifier, flow, started: Date.now() }));

  const url = new URL(protocol + "auth", location.origin);
  url.search = new URLSearchParams({
    client_id: clientId, redirect_uri: callback.href, response_type: "code",
    scope: "openid profile", state, code_challenge: challenge, code_challenge_method: "S256",
  });
  if (flow === "password") url.searchParams.set("kc_action", "UPDATE_PASSWORD");
  if (replace) location.replace(url);
  else location.assign(url);
}

function feedback(message) {
  status.textContent = message;
  status.hidden = !message;
}

async function finish(query) {
  const saved = sessionStorage.getItem(transactionKey);
  sessionStorage.removeItem(transactionKey);
  history.replaceState(null, "", "/");
  const transaction = saved && JSON.parse(saved);
  if (!transaction || transaction.state !== query.get("state") ||
      Date.now() - transaction.started > 15 * 60 * 1000) {
    throw new Error("Invalid or expired login");
  }

  if (!query.has("code")) throw new Error("Authorization code missing");

  const response = await fetch(protocol + "token", {
    method: "POST",
    cache: "no-store",
    signal: AbortSignal.timeout(10000),
    body: new URLSearchParams({
      grant_type: "authorization_code", client_id: clientId, code: query.get("code"),
      redirect_uri: callback.href, code_verifier: transaction.verifier,
    }),
  });
  if (!response.ok) throw new Error("Token exchange failed");
  const tokens = await response.json();
  const profileResponse = await fetch(protocol + "userinfo", {
    headers: { Authorization: `Bearer ${tokens.access_token}` },
    cache: "no-store",
    signal: AbortSignal.timeout(10000),
  });
  if (!profileResponse.ok) throw new Error("User info failed");
  const profile = await profileResponse.json();
  document.querySelector("#title").textContent = "Minha conta";
  document.querySelector("#account-username").textContent = profile.preferred_username || "—";
  document.querySelector("#squitv-account").classList.add("signed-in");
  document.querySelector("#dashboard").hidden = false;
  document.querySelector("#overview").hidden = false;
  document.querySelector("#description").textContent = "Gerencie o acesso à sua Conta Squi TV.";
  document.querySelector("#guest").hidden = true;
  document.querySelector("#member").hidden = false;
  showPage("account");
  idToken = tokens.id_token;
  accessToken = tokens.access_token;
  void loadAccountName();
  void loadIptv();
  const action = query.get("kc_action_status");
  if (transaction.flow === "password") {
    feedback(action === "success" ? "Senha alterada." :
      action === "cancelled" ? "Alteração de senha cancelada." : "");
  }
}

for (const button of buttons) {
  button.addEventListener("click", async () => {
    for (const control of buttons) control.disabled = true;
    try {
      await start(button.dataset.flow);
    } catch {
      feedback("Não foi possível abrir a autenticação. Tente novamente.");
      for (const control of buttons) control.disabled = false;
    }
  });
}

document.querySelector("#logout").addEventListener("click", () => {
  const form = document.createElement("form");
  form.method = "POST";
  form.action = protocol + "logout";
  const params = { client_id: clientId, post_logout_redirect_uri: callback.href };
  if (idToken) params.id_token_hint = idToken;
  for (const [name, value] of Object.entries(params)) {
    const input = document.createElement("input");
    input.type = "hidden";
    input.name = name;
    input.value = value;
    form.append(input);
  }
  document.body.append(form);
  form.submit();
});

const iptv = document.querySelector("#iptv");
const iptvForm = document.querySelector("#iptv-form");
const iptvStatus = document.querySelector("#iptv-status");
const iptvSubmit = document.querySelector("#iptv-submit");
const iptvEdit = document.querySelector("#iptv-edit");
const iptvDetails = document.querySelector("#iptv-details");
const iptvUsername = document.querySelector("#iptv-user");
const iptvPassword = document.querySelector("#iptv-password");
const iptvReveal = document.querySelector("#iptv-reveal");
const savedPassword = document.querySelector("#iptv-saved-password");
let isLinked = false;
const iptvSlots = document.querySelector("#iptv-slots");
const iptvPicker = document.querySelector("#iptv-slot-picker");
const iptvAdd = document.querySelector("#iptv-add");
const iptvCancelNew = document.querySelector("#iptv-cancel-new");
let iptvLinks = [];
let maxIptvLinks = 20;
let iptvSlot = 1;
let slotGeneration = 0;
let savingLink = false;
let passwordVisible = false;
let revealGeneration = 0;
let hidePasswordTimer;
const providerRefresh = document.querySelector("#provider-refresh");
const providerStatus = document.querySelector("#provider-status");
let providerGeneration = 0;

function showIptvStatus(message) {
  iptvStatus.textContent = message;
  iptvStatus.hidden = !message;
}

function formatAccountDate(value, withTime = false) {
  if (!value) return "Não informada";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Não informada";
  const options = { dateStyle: "medium", ...(withTime ? { timeStyle: "short" } : {}) };
  return new Intl.DateTimeFormat("pt-BR", options).format(date);
}

function setIptvFormVisible(visible) {
  iptvForm.reset();
  iptvPassword.disabled = !visible;
  iptvForm.hidden = !visible;
  iptvEdit.setAttribute("aria-expanded", String(visible));
  iptvEdit.textContent = visible ? "Cancelar edição" : "Atualizar vínculo";
}

function showLinkedIptv(link) {
  isLinked = true;
  hideSavedPassword();
  document.querySelector("#iptv-user-field").hidden = true;
  iptvUsername.disabled = true;
  document.querySelector("#iptv-password-label").textContent = "Nova senha IPTV";
  document.querySelector("#account-iptv-state").textContent = "Vinculada";
  document.querySelector("#iptv-description").textContent = "Use a Conta Squi TV para entrar no app.";
  document.querySelector("#iptv-linked-user").textContent = link.providerUsername || "—";
  document.querySelector("#iptv-validated-at").textContent = formatAccountDate(link.validatedAt, true);
  document.querySelector("#iptv-expires-at").textContent = formatAccountDate(link.expiresAt);
  iptvDetails.hidden = false;
  document.querySelector("#provider-details").hidden = false;
  iptvEdit.hidden = false;
  iptvSubmit.textContent = "Salvar vínculo";
  setIptvFormVisible(false);
  refreshCatalogLink();
}

iptvEdit.addEventListener("click", () => {
  if (iptvSubmit.disabled) return;
  const show = iptvForm.hidden;
  setIptvFormVisible(show);
  showIptvStatus("");
  hideSavedPassword();
  if (show) iptvPassword.focus();
});

async function iptvRequest(credentials, path = "/api/iptv/link") {
  const headers = { Authorization: "Bearer " + accessToken };
  const options = { headers, cache: "no-store", signal: AbortSignal.timeout(12000) };
  if (credentials) {
    headers["Content-Type"] = "application/json";
    options.method = "POST";
    options.body = JSON.stringify(credentials);
  }
  const separator = path.includes("?") ? "&" : "?";
  const response = await fetch(path + separator + "slot=" + iptvSlot, options);
  const result = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(result.message ||
    (response.status === 429 ? "Muitas tentativas. Aguarde um pouco." : "Vínculo IPTV indisponível."));
  return result;
}

function renderPassword(value) {
  passwordVisible = value !== null;
  savedPassword.textContent = value ?? "••••••••";
  iptvReveal.setAttribute("aria-pressed", String(passwordVisible));
  iptvReveal.setAttribute("aria-label", passwordVisible ? "Ocultar senha IPTV" : "Mostrar senha IPTV");
  document.querySelector("#iptv-eye-slash").hidden = !passwordVisible;
}

function hideSavedPassword() {
  revealGeneration += 1;
  clearTimeout(hidePasswordTimer);
  renderPassword(null);
  iptvReveal.disabled = false;
}

iptvReveal.addEventListener("click", async () => {
  if (passwordVisible) {
    hideSavedPassword();
    return;
  }
  const generation = ++revealGeneration;
  iptvReveal.disabled = true;
  showIptvStatus("");
  try {
    const result = await iptvRequest({}, "/api/iptv/reveal");
    if (generation !== revealGeneration) return;
    if (typeof result.password !== "string" || !result.password) throw new Error("Senha IPTV indisponível.");
    renderPassword(result.password);
    hidePasswordTimer = setTimeout(hideSavedPassword, 20000);
  } catch (error) {
    if (generation === revealGeneration) showIptvStatus(error.message);
  } finally {
    if (generation === revealGeneration) iptvReveal.disabled = false;
  }
});

document.addEventListener("visibilitychange", () => {
  if (document.hidden) hideSavedPassword();
});
window.addEventListener("pagehide", hideSavedPassword);

const providerStates = {
  active: "Ativa",
  expired: "Expirada",
  disabled: "Desativada",
  banned: "Bloqueada",
  rejected: "Credenciais recusadas",
  unreported: "Não informada",
};

async function refreshProvider() {
  const generation = ++providerGeneration;
  providerRefresh.disabled = true;
  providerStatus.textContent = "Consultando o provedor…";
  providerStatus.hidden = false;
  try {
    const data = await iptvRequest(undefined, "/api/iptv/details");
    if (generation !== providerGeneration) return;
    document.querySelector("#provider-state").textContent = providerStates[data.status] || "Não informada";
    const connections = Number.isInteger(data.activeConnections) ? String(data.activeConnections) : "—";
    const limit = Number.isInteger(data.maxConnections) ? String(data.maxConnections) : null;
    document.querySelector("#provider-connections").textContent = limit ? connections + " de " + limit : connections;
    document.querySelector("#provider-checked-at").textContent = formatAccountDate(data.checkedAt, true);
    document.querySelector("#iptv-expires-at").textContent = formatAccountDate(data.expiresAt);
    providerStatus.hidden = true;
  } catch (error) {
    if (generation !== providerGeneration) return;
    providerStatus.textContent = error.name === "TimeoutError" ? "O provedor demorou a responder." : error.message;
  } finally {
    if (generation === providerGeneration) providerRefresh.disabled = false;
  }
}

providerRefresh.addEventListener("click", refreshProvider);
document.querySelector("#provider-details").addEventListener("toggle", event => {
  if (event.currentTarget.open && isLinked) void refreshProvider();
});

function resetIptv() {
  cloudGeneration += 1;
  cloudLibrary.hidden = true;
  resetCategoryEditor();
  resetProgressView();
  providerGeneration += 1;
  hideSavedPassword();
  isLinked = false;
  refreshCatalogLink();
  iptv.hidden = false;
  iptvEdit.hidden = true;
  iptvDetails.hidden = true;
  const providerDetails = document.querySelector("#provider-details");
  providerDetails.open = false;
  providerDetails.hidden = true;
  providerStatus.hidden = true;
  for (const id of ["#provider-state", "#provider-connections", "#provider-checked-at"]) {
    document.querySelector(id).textContent = "—";
  }
  setIptvFormVisible(false);
  showIptvStatus("");
  const state = document.querySelector("#account-iptv-state");
  const description = document.querySelector("#iptv-description");
  state.textContent = "Consultando…";
  description.textContent = "";
}

function showUnlinkedIptv() {
  document.querySelector("#account-iptv-state").textContent = "Pendente";
  document.querySelector("#iptv-description").textContent =
    "Vincule a conta que você já usa no provedor do Squi TV.";
  document.querySelector("#iptv-user-field").hidden = false;
  document.querySelector("#iptv-password-label").textContent = "Senha IPTV";
  iptvUsername.disabled = false;
  iptvSubmit.textContent = "Vincular conta";
  setIptvFormVisible(true);
}

function updateLinkPicker(data) {
  iptvLinks = data.links ?? [];
  maxIptvLinks = data.maxLinks ?? 20;
  iptvSlots.hidden = iptvLinks.length === 0;
  iptvPicker.replaceChildren(...iptvLinks.map(link => new Option(link.username, String(link.slot))));
  iptvPicker.value = String(iptvSlot);
  iptvAdd.hidden = iptvLinks.length >= maxIptvLinks;
  iptvCancelNew.hidden = true;
}

async function loadIptv() {
  const generation = ++slotGeneration;
  resetIptv();
  iptvSubmit.disabled = true;
  try {
    const link = await iptvRequest();
    if (generation !== slotGeneration) return;
    updateLinkPicker(link);
    if (link.linked) {
      showLinkedIptv(link);
    } else if (iptvLinks.length) {
      iptvSlot = iptvLinks[0].slot;
      void loadIptv();
    } else {
      showUnlinkedIptv();
    }
  } catch (error) {
    if (generation !== slotGeneration) return;
    document.querySelector("#account-iptv-state").textContent = "Indisponível";
    showIptvStatus(error.name === "TimeoutError" ? "O vínculo IPTV demorou a responder." : error.message);
  } finally {
    if (generation === slotGeneration) iptvSubmit.disabled = false;
  }
}

iptvPicker.addEventListener("change", () => {
  if (savingLink || !accessToken) return;
  iptvSlot = Number(iptvPicker.value);
  void loadIptv();
});

iptvAdd.addEventListener("click", () => {
  if (savingLink || !accessToken) return;
  const occupied = new Set(iptvLinks.map(link => link.slot));
  let slot = 1;
  while (slot <= maxIptvLinks && occupied.has(slot)) slot += 1;
  if (slot > maxIptvLinks) return;
  slotGeneration += 1;
  iptvSlot = slot;
  iptvPicker.selectedIndex = -1;
  iptvAdd.hidden = true;
  iptvCancelNew.hidden = false;
  resetIptv();
  showUnlinkedIptv();
  iptvSubmit.disabled = false;
  iptvUsername.focus();
});

iptvCancelNew.addEventListener("click", () => {
  if (savingLink || !iptvLinks.length) return;
  iptvSlot = iptvLinks[0].slot;
  void loadIptv();
});

function setIptvLinkControlsDisabled(disabled) {
  for (const control of [iptvPicker, iptvAdd, iptvCancelNew]) control.disabled = disabled;
}

iptvForm.addEventListener("submit", async event => {
  event.preventDefault();
  if (!accessToken || iptvSubmit.disabled || iptvForm.hidden) return;
  savingLink = true;
  setIptvLinkControlsDisabled(true);
  hideSavedPassword();
  iptvSubmit.disabled = true;
  showIptvStatus("Validando com o provedor…");
  try {
    const credentials = { password: iptvPassword.value };
    if (!isLinked) credentials.username = iptvUsername.value;
    const link = await iptvRequest(credentials);
    updateLinkPicker(link);
    showLinkedIptv(link);
    iptvEdit.focus();
    showIptvStatus("Conta IPTV vinculada.");
  } catch (error) {
    showIptvStatus(error.name === "TimeoutError" ? "A validação demorou a responder." : error.message);
  } finally {
    iptvPassword.value = "";
    iptvSubmit.disabled = false;
    savingLink = false;
    setIptvLinkControlsDisabled(false);
  }
});

const accountNameForm = document.querySelector("#account-name-form");
const accountName = document.querySelector("#account-display-name");
const accountNameSave = document.querySelector("#account-name-save");
const accountNameStatus = document.querySelector("#account-name-status");

function nameFeedback(message) {
  accountNameStatus.textContent = message;
  accountNameStatus.hidden = !message;
}

async function loadAccountName() {
  try {
    const settings = await cloudRequest("account");
    accountName.value = settings.displayName ?? "";
    accountNameSave.disabled = false;
  } catch (error) {
    nameFeedback(error.message);
  }
}

accountNameForm.addEventListener("submit", async event => {
  event.preventDefault();
  if (accountNameSave.disabled || !accessToken) return;
  accountNameSave.disabled = true;
  nameFeedback("");
  try {
    const saved = await cloudRequest("account", null, null, {displayName: accountName.value.trim()});
    accountName.value = saved.displayName ?? "";
    nameFeedback(saved.displayName ? "Nome salvo." : "O app voltará a exibir seu usuário.");
  } catch (error) {
    nameFeedback(error.message);
  } finally {
    accountNameSave.disabled = false;
  }
});

const cloudLibrary = document.querySelector("#cloud-library");
const cloudPicker = document.querySelector("#cloud-profile");
const cloudStatus = document.querySelector("#cloud-status");
const cloudList = document.querySelector("#cloud-favorites");
const cloudRename = document.querySelector("#cloud-profile-rename");
let cloudGeneration = 0;
let cloudProfiles = [];
let cloudWriting = false;

function cloudFeedback(text) {
  cloudStatus.textContent = text;
  cloudStatus.hidden = !text;
}

async function cloudRequest(resource, profile, after, body, extra = {}) {
  const params = new URLSearchParams({...extra, slot: String(iptvSlot)});
  if (profile) params.set("profile", profile);
  if (after) params.set("after", String(after));
  const headers = {Authorization: "Bearer " + accessToken};
  const options = {headers, cache: "no-store", signal: AbortSignal.timeout(10000)};
  if (body) {
    headers["Content-Type"] = "application/json";
    options.method = "POST";
    options.body = JSON.stringify(body);
  }
  const response = await fetch("/api/sync/" + resource + "?" + params, options);
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.message || "Não foi possível acessar os dados sincronizados.");
  return data;
}

async function loadCloudProfiles(preferred = cloudPicker.value) {
  resetCategoryEditor();
  resetProgressView();
  const generation = ++cloudGeneration;
  cloudLibrary.hidden = false;
  cloudPicker.disabled = true;
  cloudRename.disabled = true;
  cloudList.replaceChildren();
  cloudFeedback("Carregando perfis…");
  try {
    const data = await cloudRequest("profiles");
    if (generation !== cloudGeneration) return;
    cloudProfiles = data.profiles;
    cloudPicker.replaceChildren(...cloudProfiles.map(profile => new Option(profile.name, profile.id)));
    if (cloudProfiles.some(profile => profile.id === preferred)) cloudPicker.value = preferred;
    cloudPicker.disabled = !cloudProfiles.length;
    cloudRename.disabled = !cloudProfiles.length;
    categoryPanel.hidden = !cloudProfiles.length;
    progressPanel.hidden = !cloudProfiles.length;
    if (cloudProfiles.length) void loadCloudFavorites();
    else cloudFeedback("Crie um perfil ou crie pelo app ao sincronizar.");
  } catch (error) {
    if (generation === cloudGeneration) cloudFeedback(error.message);
  }
}

const cloudKinds = {live: "Canal", movie: "Filme", series: "Série"};

async function loadCloudFavorites() {
  const profile = cloudPicker.value;
  const generation = ++cloudGeneration;
  cloudList.replaceChildren();
  if (!profile) return;
  cloudFeedback("Carregando favoritos…");
  try {
    const items = new Map();
    let after = 0;
    for (let page = 0; page < 120; page += 1) {
      const result = await cloudRequest("favorites", profile, after);
      if (generation !== cloudGeneration) return;
      for (const item of result.items) items.set(item.kind + "/" + item.id, item);
      if (!result.hasMore) break;
      if (result.next <= after || page === 119) throw new Error("A lista de favoritos excede o limite de consulta.");
      after = result.next;
    }
    const favorites = [...items.values()].filter(item => !item.deleted);
    cloudFeedback(favorites.length ? "" : "Nenhum favorito sincronizado neste perfil.");
    for (const item of favorites) {
      const row = document.createElement("li");
      const label = document.createElement("span");
      label.textContent = item.title || cloudKinds[item.kind] + " #" + item.id;
      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "secondary";
      remove.textContent = "Remover";
      remove.setAttribute("aria-label", "Remover " + label.textContent + " dos favoritos");
      remove.addEventListener("click", async () => {
        if (cloudWriting || generation !== cloudGeneration) return;
        cloudWriting = true;
        remove.disabled = true;
        try {
          await cloudRequest("favorites", profile, null, {...item, deleted: true});
          if (generation === cloudGeneration) {
            row.remove();
            if (!cloudList.childElementCount) cloudFeedback("Nenhum favorito sincronizado neste perfil.");
          }
        } catch (error) {
          if (generation === cloudGeneration) cloudFeedback(error.message);
        } finally {
          cloudWriting = false;
          remove.disabled = false;
        }
      });
      row.append(label, remove);
      cloudList.append(row);
    }
  } catch (error) {
    if (generation === cloudGeneration) cloudFeedback(error.message);
  }
}

cloudPicker.addEventListener("change", () => {
  resetCategoryEditor();
  resetProgressView();
  void loadCloudFavorites();
});
document.querySelector("#cloud-profile-form").addEventListener("submit", async event => {
  event.preventDefault();
  if (cloudWriting || cloudLibrary.hidden) return;
  const input = document.querySelector("#cloud-profile-name");
  const action = event.submitter?.value || "create";
  const profile = cloudProfiles.find(item => item.id === cloudPicker.value);
  if (action === "rename" && !profile) return;
  const data = {action, name: input.value.trim()};
  if (action === "rename") Object.assign(data, {id: profile.id, revision: profile.revision});
  const generation = cloudGeneration;
  cloudWriting = true;
  try {
    const saved = await cloudRequest("profiles", null, null, data);
    if (generation === cloudGeneration) {
      input.value = "";
      void loadCloudProfiles(saved.id);
    }
  } catch (error) {
    if (generation === cloudGeneration) cloudFeedback(error.message);
  } finally {
    cloudWriting = false;
  }
});

const categoryPanel = document.querySelector("#cloud-categories");
const categoryKind = document.querySelector("#cloud-category-kind");
const categoryForm = document.querySelector("#cloud-category-form");
const categoryOpen = document.querySelector("#cloud-category-open");
const categoryMode = document.querySelector("#cloud-category-mode");
const categoryList = document.querySelector("#cloud-category-list");
const categorySearch = document.querySelector("#cloud-category-search");
const categoryStatus = document.querySelector("#cloud-category-status");
let categoryGeneration = 0;
let categoryRows = [];
let categoryDraft;
let categorySaving = false;

function categoryFeedback(text) {
  categoryStatus.textContent = text;
  categoryStatus.hidden = !text;
}

function resetCategoryEditor() {
  categoryGeneration += 1;
  categoryDraft = null;
  categoryForm.hidden = true;
  categorySearch.value = "";
  categoryList.replaceChildren();
  categoryOpen.disabled = false;
  categoryFeedback("");
}

function showCategoryRows() {
  categoryList.replaceChildren();
  const query = categorySearch.value.trim().toLocaleLowerCase("pt-BR");
  const matches = categoryRows.map((entry, index) => ({entry, index})).filter(({entry}) =>
    !query || entry.id.includes(query) || entry.name.toLocaleLowerCase("pt-BR").includes(query)
  );
  if (!matches.length && query) {
    const empty = document.createElement("li");
    empty.textContent = "Nenhuma categoria corresponde à busca.";
    categoryList.append(empty);
  }
  for (const [position, {entry, index}] of matches.entries()) {
    const row = document.createElement("li");
    const label = document.createElement("label");
    const check = document.createElement("input");
    check.type = "checkbox";
    check.checked = !categoryDraft.hidden.has(entry.id);
    check.addEventListener("change", () => {
      if (check.checked) categoryDraft.hidden.delete(entry.id);
      else categoryDraft.hidden.add(entry.id);
    });
    label.append(check, " " + entry.name);
    row.append(label);
    for (const [offset, icon, description] of [[-1, "↑", "Subir"], [1, "↓", "Descer"]]) {
      const move = document.createElement("button");
      move.type = "button";
      move.className = "secondary";
      move.textContent = icon;
      move.setAttribute("aria-label", description + " " + entry.name);
      const target = matches[position + offset]?.index;
      move.disabled = target === undefined;
      move.addEventListener("click", () => {
        if (target === undefined) return;
        [categoryRows[index], categoryRows[target]] = [categoryRows[target], categoryRows[index]];
        showCategoryRows();
      });
      row.append(move);
    }
    categoryList.append(row);
  }
}

categoryOpen.addEventListener("click", async () => {
  if (!cloudPicker.value || categorySaving) return;
  resetCategoryEditor();
  const generation = categoryGeneration;
  const profile = cloudPicker.value;
  const kind = categoryKind.value;
  categoryOpen.disabled = true;
  categoryFeedback("Carregando categorias…");
  try {
    const [stored, catalogResult, preparation] = await Promise.all([
      cloudRequest("categories", profile),
      iptvRequest(undefined, "/api/iptv/categories?kind=" + encodeURIComponent(kind))
        .then(catalog => ({catalog}), error => ({error})),
      kind === "live" ? null : cloudRequest("catalog", null, null, null, {kind}).catch(() => null),
    ]);
    if (generation !== categoryGeneration) return;
    const previous = stored.items.find(item => item.kind === kind);
    if (!previous && catalogResult.error) throw catalogResult.error;
    const known = new Map((catalogResult.catalog?.items ?? []).map(item => [item.id, item]));
    const ids = [...new Set([...(previous?.order ?? []), ...known.keys(), ...(previous?.hidden ?? [])])];
    categoryRows = ids.map(id => known.get(id) ?? {id, name: "Categoria #" + id});
    categoryDraft = {profile, kind, revision: previous?.revision ?? 0, hidden: new Set(previous?.hidden)};
    categoryMode.value = previous?.mode ?? "provider";
    document.querySelector("#cloud-category-mode-row").hidden = kind === "live";
    categoryForm.hidden = false;
    if (catalogResult.error) {
      categoryFeedback("A organização salva foi carregada. O catálogo do provedor está indisponível no momento.");
    } else if (Number.isInteger(catalogResult.catalog?.indexedItems)) {
      const count = catalogResult.catalog.indexedItems.toLocaleString("pt-BR");
      const percent = preparation?.available && preparation.total > 0
        ? Math.floor(100 * preparation.prepared / preparation.total) : null;
      const details = percent === null ? "" : ` Detalhes preparados: ${percent}% (${preparation.prepared.toLocaleString("pt-BR")} de ${count}).`;
      const failures = preparation?.failed > 0 ? ` ${preparation.failed.toLocaleString("pt-BR")} aguardando nova tentativa.` : "";
      categoryFeedback(`${count} itens na listagem do servidor.${details}${failures}${previous ? " Organização deste perfil carregada." : ""}`);
    } else if (previous) {
      categoryFeedback("Organização salva neste perfil.");
    } else {
      categoryFeedback(categoryRows.length ? "Este perfil ainda usa a organização padrão." : "O provedor não retornou categorias.");
    }
    showCategoryRows();
  } catch (error) {
    if (generation === categoryGeneration) categoryFeedback(error.message);
  } finally {
    if (generation === categoryGeneration) categoryOpen.disabled = false;
  }
});

categoryKind.addEventListener("change", resetCategoryEditor);
categorySearch.addEventListener("input", showCategoryRows);
categorySearch.addEventListener("keydown", event => { if (event.key === "Enter") event.preventDefault(); });
categoryForm.addEventListener("submit", async event => {
  event.preventDefault();
  if (!categoryDraft || categorySaving) return;
  const generation = categoryGeneration;
  const data = {
    kind: categoryDraft.kind, mode: categoryMode.value,
    order: categoryRows.map(item => item.id), hidden: [...categoryDraft.hidden],
    revision: categoryDraft.revision,
  };
  categorySaving = true;
  try {
    const saved = await cloudRequest("categories", categoryDraft.profile, null, {items: [data]});
    if (generation === categoryGeneration) {
      categoryDraft.revision = saved.items[0].revision;
      categoryFeedback("Organização salva. Use o app para aplicar as mudanças.");
    }
  } catch (error) {
    if (generation === categoryGeneration) categoryFeedback(error.message);
  } finally {
    categorySaving = false;
  }
});

const progressPanel = document.querySelector("#cloud-progress");
const progressOpen = document.querySelector("#cloud-progress-open");
const progressList = document.querySelector("#cloud-progress-list");
const progressStatus = document.querySelector("#cloud-progress-status");
let progressGeneration = 0;

function progressFeedback(text) {
  progressStatus.textContent = text;
  progressStatus.hidden = !text;
}

function resetProgressView() {
  progressGeneration += 1;
  progressList.replaceChildren();
  progressFeedback("");
  progressOpen.disabled = false;
}

progressOpen.addEventListener("click", async () => {
  const profile = cloudPicker.value;
  if (!profile) return;
  resetProgressView();
  const generation = progressGeneration;
  progressOpen.disabled = true;
  progressFeedback("Carregando progresso…");
  try {
    const rows = new Map();
    let after = 0;
    for (let page = 0; page < 125; page += 1) {
      const data = await cloudRequest("progress", profile, after);
      if (generation !== progressGeneration) return;
      for (const row of data.items) rows.set(row.kind + "/" + row.id, row);
      if (!data.hasMore) break;
      if (data.next <= after || page === 124) throw new Error("A lista de progresso excede o limite de consulta.");
      after = data.next;
    }
    const items = [...rows.values()].filter(row => !row.deleted).sort((a, b) => b.observedAt - a.observedAt);
    progressFeedback(items.length ? "" : "Nenhum progresso novo foi sincronizado neste perfil.");
    for (const item of items) {
      const row = document.createElement("li");
      const label = document.createElement("span");
      const title = item.title || (item.kind === "episode" ? "Episódio #" : "Filme #") + item.id;
      const percent = item.durationMs ? Math.min(100, Math.floor(100 * item.positionMs / item.durationMs)) + "%" : "Em andamento";
      label.textContent = title + " · " + (item.completed ? "Concluído" : percent);
      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "secondary";
      remove.textContent = "Remover";
      remove.setAttribute("aria-label", "Remover progresso de " + title);
      remove.addEventListener("click", async () => {
        if (generation !== progressGeneration || remove.disabled) return;
        remove.disabled = true;
        try {
          await cloudRequest("progress", profile, null, {
            action: "delete", kind: item.kind, id: item.id, revision: item.revision,
          });
          if (generation === progressGeneration) {
            row.remove();
            if (!progressList.childElementCount) progressFeedback("Nenhum progresso sincronizado neste perfil.");
          }
        } catch (error) {
          if (generation === progressGeneration) progressFeedback(error.message);
        } finally {
          remove.disabled = false;
        }
      });
      row.append(label, remove);
      progressList.append(row);
    }
  } catch (error) {
    if (generation === progressGeneration) progressFeedback(error.message);
  } finally {
    if (generation === progressGeneration) progressOpen.disabled = false;
  }
});

function showLoginRetry() {
  document.querySelector("#guest").hidden = false;
  document.querySelector("#description").textContent = "Entre para acessar sua conta.";
  feedback("Não foi possível concluir o acesso. Tente novamente.");
  for (const button of buttons) button.disabled = false;
}

const pageButtons = [...document.querySelectorAll("[data-page]")];
const kindButtons = [...document.querySelectorAll("[data-kind]")];
const browse = document.querySelector("#browse");
const browseList = document.querySelector("#browse-items");
const browseMore = document.querySelector("#browse-more");
const hero = document.querySelector("#catalog-hero");
const dialog = document.querySelector("#item-dialog");
let sitePage = "account";
let initialPageChosen = false;
let browseKind = "movie";
let browseOffset = 0;
let browseQuery = "";
let browseRevision;
let browseGeneration = 0;
let detailGeneration = 0;
let browseReady = false;
let libraryRequested = false;
let heroItem;

function showPage(page) {
  sitePage = page;
  if (page !== "account") hideSavedPassword();
  for (const button of pageButtons) {
    if (button.dataset.page === page) button.setAttribute("aria-current", "page");
    else button.removeAttribute("aria-current");
  }
  for (const pane of document.querySelectorAll("[data-pane]")) {
    pane.toggleAttribute("data-inactive", pane.dataset.pane !== page);
  }
  if (page === "catalog" && isLinked && !browseReady && !browseMore.disabled) void loadCatalog();
  if (page === "library" && isLinked && !libraryRequested) {
    libraryRequested = true;
    void loadCloudProfiles();
  }
}

function refreshCatalogLink() {
  browseGeneration += 1;
  browseReady = false;
  libraryRequested = false;
  browseMore.disabled = false;
  if (dialog.open) dialog.close();
  browse.hidden = !isLinked;
  for (const button of pageButtons) button.disabled = button.dataset.page !== "account" && !isLinked;
  if (!isLinked) {
    if (sitePage !== "account") showPage("account");
    return;
  }
  if (!initialPageChosen) {
    initialPageChosen = true;
    showPage("catalog");
  } else if (sitePage === "catalog") {
    void loadCatalog();
  }
}

function setText(selector, text) {
  const element = document.querySelector(selector);
  element.textContent = text || "";
  element.hidden = !text;
}

function setArtwork(image, value, lazy = true) {
  image.hidden = true;
  image.removeAttribute("src");
  if (!value) return;
  let url;
  try { url = new URL(value); } catch { return; }
  if (url.protocol !== "https:" || url.username || url.password) return;
  image.loading = lazy ? "lazy" : "eager";
  image.decoding = "async";
  image.referrerPolicy = "no-referrer";
  image.onerror = () => { image.hidden = true; };
  image.src = url.href;
  image.hidden = false;
}

function itemMeta(item, kind) {
  const kindName = {live: "Ao vivo", movie: "Filme", series: "Série"}[kind];
  const year = item.releaseDate?.match(/^\d{4}/)?.[0];
  return [kindName, year, item.genre?.split(",").slice(0, 2).join(", ")].filter(Boolean).join(" · ");
}

function renderHero(items) {
  heroItem = browseQuery || browseKind === "live" ? null :
    items.find(item => item.backdrop && item.plot) || items.find(item => item.backdrop) || items[0];
  hero.hidden = !heroItem;
  if (!heroItem) return;
  setText("#hero-kind", itemMeta(heroItem, browseKind));
  setText("#hero-title", heroItem.name);
  setText("#hero-description", heroItem.plot);
  setArtwork(document.querySelector("#hero-image"), heroItem.backdrop, false);
}

function catalogCard(item, kind) {
  const row = document.createElement("li");
  const button = document.createElement("button");
  button.type = "button";
  button.className = "browse-card";
  const art = document.createElement("span");
  art.className = "card-art";
  const fallback = document.createElement("span");
  fallback.textContent = item.name.slice(0, 2).toLocaleUpperCase("pt-BR");
  fallback.setAttribute("aria-hidden", "true");
  const image = document.createElement("img");
  image.alt = "";
  setArtwork(image, item.poster);
  art.append(fallback, image);
  const title = document.createElement("strong");
  title.className = "card-name";
  title.textContent = item.name;
  const meta = document.createElement("span");
  meta.className = "card-meta";
  meta.textContent = itemMeta(item, kind);
  button.append(art, title, meta);
  button.addEventListener("click", () => void showCatalogDetail(item, kind));
  row.append(button);
  return row;
}

async function loadCatalog(append = false) {
  const generation = ++browseGeneration;
  const kind = browseKind;
  if (!append) {
    browseOffset = 0;
    browseRevision = undefined;
    browseReady = false;
    browseList.replaceChildren();
    browseList.dataset.kind = kind;
    hero.hidden = true;
    browseMore.hidden = true;
  }
  browseMore.disabled = true;
  setText("#browse-status", append ? "Carregando mais itens…" : "Carregando catálogo…");
  try {
    const args = {kind, view: "browse", offset: String(browseOffset), q: browseQuery};
    if (browseRevision !== undefined) args.revision = String(browseRevision);
    const data = await cloudRequest("catalog", null, null, null, args);
    if (generation !== browseGeneration) return;
    if (data.changed) { await loadCatalog(); return; }
    browseReady = true;
    if (!data.available) {
      browseReady = false;
      browseMore.textContent = "Atualizar catálogo";
      browseMore.hidden = false;
      setText("#browse-status", "O servidor ainda está preparando a listagem deste vínculo.");
      return;
    }
    browseRevision = data.revision;
    browseOffset = data.next;
    if (!append) renderHero(data.items);
    browseList.append(...data.items.map(item => catalogCard(item, kind)));
    browseMore.hidden = !data.hasMore;
    setText("#browse-status", browseList.childElementCount ? "" : "Nenhum item encontrado.");
    if (data.tmdb) {
      document.querySelector("#catalog-credits").hidden = false;
      setArtwork(document.querySelector("#tmdb-logo"), data.tmdb, false);
    }
  } catch (error) {
    if (generation === browseGeneration) {
      setText("#browse-status", error.message);
      browseMore.textContent = "Tentar novamente";
      browseMore.hidden = false;
    }
  } finally {
    if (generation === browseGeneration) browseMore.disabled = false;
  }
}

async function showCatalogDetail(item, kind) {
  const generation = ++detailGeneration;
  const extra = document.querySelector("#item-dialog-extra");
  extra.replaceChildren();
  extra.hidden = true;
  setText("#item-dialog-title", item.name);
  setText("#item-dialog-meta", itemMeta(item, kind));
  setText("#item-dialog-plot", item.plot);
  setText("#item-dialog-status", "Buscando detalhes…");
  const image = document.querySelector("#item-dialog-image");
  setArtwork(image, item.backdrop || item.poster, false);
  dialog.showModal();
  try {
    const result = await cloudRequest("catalog", null, null, null, {kind, view: "detail", id: String(item.id)});
    if (generation !== detailGeneration || !dialog.open) return;
    if (!result.data) {
      setText("#item-dialog-status", item.plot ? "" : "O servidor ainda está preparando os detalhes deste título.");
      return;
    }
    const data = result.data;
    if (data.metadata_logo) {
      document.querySelector("#catalog-credits").hidden = false;
      setArtwork(document.querySelector("#tmdb-logo"), data.metadata_logo, false);
    }
    setText("#item-dialog-plot", data.info?.plot || item.plot);
    setText("#item-dialog-meta", itemMeta({...item, ...data.info}, kind));
    setArtwork(image, data.info?.backdrop_path?.[0] || item.backdrop || item.poster, false);
    if (kind === "series") {
      for (const [season, episodes] of Object.entries(data.episodes || {}).sort(([a], [b]) => Number(a) - Number(b))) {
        const row = document.createElement("li");
        const disclosure = document.createElement("details");
        const summary = document.createElement("summary");
        summary.textContent = `Temporada ${season} · ${episodes.length} ${episodes.length === 1 ? "episódio" : "episódios"}`;
        disclosure.append(summary);
        disclosure.addEventListener("toggle", () => {
          if (!disclosure.open || disclosure.querySelector("ul")) return;
          const list = document.createElement("ul");
          list.className = "episode-previews";
          for (const episode of episodes) {
            const entry = document.createElement("li");
            const label = document.createElement("span");
            label.textContent = [episode.episode_num, episode.title].filter(Boolean).join(" · ") || "Episódio";
            const thumb = document.createElement("img");
            thumb.alt = "";
            setArtwork(thumb, episode.info?.movie_image);
            entry.append(thumb, label);
            list.append(entry);
          }
          disclosure.append(list);
        });
        row.append(disclosure);
        extra.append(row);
      }
    } else if (kind === "live") {
      for (const programme of data.epg_listings || []) {
        const name = epgTitle(programme.title);
        if (!name) continue;
        const time = Number.isFinite(programme.start_timestamp)
          ? new Intl.DateTimeFormat("pt-BR", {hour: "2-digit", minute: "2-digit"}).format(programme.start_timestamp * 1000)
          : "";
        const row = document.createElement("li");
        row.textContent = [time, name].filter(Boolean).join(" · ");
        extra.append(row);
      }
    } else {
      for (const [label, value] of [["Direção", data.info?.director], ["Elenco", data.info?.cast]]) {
        if (!value) continue;
        const row = document.createElement("li");
        row.textContent = `${label} · ${value}`;
        extra.append(row);
      }
    }
    for (const credit of data.info?.editorialCredits || []) {
      if (!["TVmaze", "Wikidata"].includes(credit.source)) continue;
      const row = document.createElement("li");
      const source = document.createElement("a");
      const license = document.createElement("a");
      source.href = credit.url;
      source.textContent = credit.source;
      license.href = credit.license;
      license.textContent = credit.source === "TVmaze" ? "CC BY-SA" : "CC0";
      row.append("Dados de ", source, " · ", license);
      extra.append(row);
    }
    extra.hidden = !extra.childElementCount;
    setText("#item-dialog-status", kind === "live" && extra.hidden ? "Sem programação disponível." : "");
  } catch (error) {
    if (generation === detailGeneration && dialog.open) setText("#item-dialog-status", error.message);
  }
}

function epgTitle(value) {
  if (typeof value !== "string") return "";
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(value) || value.length < 4) return value;
  try {
    const bytes = Uint8Array.from(atob(value), char => char.charCodeAt(0));
    const decoded = new TextDecoder("utf-8", {fatal: true}).decode(bytes);
    return decoded && !/[\u0000-\u001f\u007f]/.test(decoded) ? decoded : value;
  } catch { return value; }
}

for (const button of pageButtons) {
  button.addEventListener("click", () => {
    initialPageChosen = true;
    showPage(button.dataset.page);
    window.scrollTo(0, 0);
  });
}
for (const button of kindButtons) {
  button.addEventListener("click", () => {
    if (browseKind === button.dataset.kind) return;
    browseKind = button.dataset.kind;
    for (const choice of kindButtons) choice.setAttribute("aria-pressed", String(choice === button));
    browseMore.textContent = "Carregar mais";
    void loadCatalog();
  });
}
document.querySelector("#browse-search").addEventListener("submit", event => {
  event.preventDefault();
  browseQuery = document.querySelector("#browse-query").value.trim();
  browseMore.textContent = "Carregar mais";
  void loadCatalog();
});
browseMore.addEventListener("click", () => {
  browseMore.textContent = "Carregar mais";
  void loadCatalog(browseReady);
});
document.querySelector("#hero-detail").addEventListener("click", () => {
  if (heroItem) void showCatalogDetail(heroItem, browseKind);
});
dialog.addEventListener("close", () => { detailGeneration += 1; });

const loginQuery = new URLSearchParams(location.search);
if (location.pathname === callback.pathname && (loginQuery.has("code") || loginQuery.has("error"))) {
  finish(loginQuery).catch(showLoginRetry);
} else {
  if (location.pathname === callback.pathname) history.replaceState(null, "", "/");
  start("login", true).catch(showLoginRetry);
}
