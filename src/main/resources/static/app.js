(() => {
  "use strict";

  const API_ITEMS = "/api/v1/items";
  const API_SEARCH = "/api/v1/search";
  const PAGE_SIZE = 20;

  const saveForm = document.getElementById("save-form");
  const urlInput = document.getElementById("url-input");
  const noteInput = document.getElementById("note-input");
  const saveButton = document.getElementById("save-button");
  const saveStatus = document.getElementById("save-status");

  const searchForm = document.getElementById("search-form");
  const searchInput = document.getElementById("search-input");
  const clearSearchButton = document.getElementById("clear-search-button");

  const listTitle = document.getElementById("list-title");
  const listStatus = document.getElementById("list-status");
  const itemList = document.getElementById("item-list");
  const refreshButton = document.getElementById("refresh-button");
  const loadMoreWrap = document.getElementById("load-more-wrap");
  const loadMoreButton = document.getElementById("load-more-button");
  const itemTemplate = document.getElementById("item-template");

  /** @type {{mode: "list"|"search", query: string, page: number, totalPages: number}} */
  const state = { mode: "list", query: "", page: 0, totalPages: 0 };

  function setStatus(el, message, kind) {
    el.textContent = message || "";
    el.classList.remove("error", "success", "info");
    if (message && kind) el.classList.add(kind);
  }

  async function parseErrorResponse(response) {
    try {
      const body = await response.json();
      if (body && body.message) {
        const fieldDetails = Array.isArray(body.fieldErrors) && body.fieldErrors.length
          ? " (" + body.fieldErrors.map((f) => `${f.field}: ${f.message}`).join(", ") + ")"
          : "";
        return body.message + fieldDetails;
      }
    } catch (_) {
      // response had no JSON body; fall through to generic message
    }
    return `Request failed (${response.status})`;
  }

  async function apiFetch(url, options) {
    const response = await fetch(url, options);
    if (response.status === 401) {
      throw new Error(
        "Not authenticated. This build has no login UI yet — either run the backend " +
        "with the local dev-auth bypass (DEV_AUTH_ENABLED=true) or save items via the " +
        "Telegram bot."
      );
    }
    if (!response.ok) {
      throw new Error(await parseErrorResponse(response));
    }
    if (response.status === 204) return null;
    return response.json();
  }

  function statusBadge(processingStatus) {
    switch (processingStatus) {
      case "PENDING":
      case "PROCESSING":
        return "fetching details…";
      case "FAILED":
        return "details unavailable";
      default:
        return null;
    }
  }

  function formatDate(isoString) {
    if (!isoString) return "";
    try {
      return new Date(isoString).toLocaleString();
    } catch (_) {
      return isoString;
    }
  }

  function renderItem(item) {
    const node = itemTemplate.content.firstElementChild.cloneNode(true);
    node.dataset.id = item.id;

    const titleLink = node.querySelector(".item-title");
    titleLink.textContent = item.title || item.url;
    titleLink.href = item.url;

    const badge = node.querySelector(".badge");
    const badgeText = statusBadge(item.processingStatus);
    if (badgeText) {
      badge.textContent = badgeText;
      badge.hidden = false;
    }

    const description = node.querySelector(".item-description");
    description.textContent = item.description || "";
    description.hidden = !item.description;

    const note = node.querySelector(".item-note");
    note.textContent = item.userNote ? `Note: ${item.userNote}` : "";
    note.hidden = !item.userNote;

    const meta = node.querySelector(".item-meta");
    meta.textContent = `Saved ${formatDate(item.savedAt)}`;

    const editForm = node.querySelector(".edit-form");
    const editTitleInput = node.querySelector(".edit-title-input");
    const editNoteInput = node.querySelector(".edit-note-input");
    const editButton = node.querySelector(".edit-button");
    const cancelEditButton = node.querySelector(".cancel-edit-button");
    const deleteButton = node.querySelector(".delete-button");

    editButton.addEventListener("click", () => {
      editTitleInput.value = item.title || "";
      editNoteInput.value = item.userNote || "";
      editForm.hidden = false;
      editButton.hidden = true;
    });

    cancelEditButton.addEventListener("click", () => {
      editForm.hidden = true;
      editButton.hidden = false;
    });

    editForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      try {
        const updated = await apiFetch(`${API_ITEMS}/${item.id}`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            title: editTitleInput.value || null,
            userNote: editNoteInput.value || null,
          }),
        });
        Object.assign(item, updated);
        const refreshed = renderItem(item);
        node.replaceWith(refreshed);
        setStatus(listStatus, "Item updated.", "success");
      } catch (err) {
        setStatus(listStatus, err.message, "error");
      }
    });

    deleteButton.addEventListener("click", async () => {
      if (!window.confirm("Delete this saved item?")) return;
      try {
        await apiFetch(`${API_ITEMS}/${item.id}`, { method: "DELETE" });
        node.remove();
        setStatus(listStatus, "Item deleted.", "success");
        if (!itemList.children.length) renderEmptyState();
      } catch (err) {
        setStatus(listStatus, err.message, "error");
      }
    });

    return node;
  }

  function renderEmptyState() {
    itemList.innerHTML = "";
    const li = document.createElement("li");
    li.className = "empty-state";
    li.textContent = state.mode === "search"
      ? "No saved items match your search."
      : "Nothing saved yet. Add a link above to get started.";
    itemList.appendChild(li);
  }

  function currentListUrl(page) {
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
    if (state.mode === "search") {
      params.set("q", state.query);
      return `${API_SEARCH}?${params.toString()}`;
    }
    return `${API_ITEMS}?${params.toString()}`;
  }

  async function loadPage(page, { append } = { append: false }) {
    setStatus(listStatus, "Loading…", "info");
    try {
      const data = await apiFetch(currentListUrl(page));
      state.page = data.page;
      state.totalPages = data.totalPages;

      if (!append) itemList.innerHTML = "";

      if (!append && (!data.content || data.content.length === 0)) {
        renderEmptyState();
      } else {
        for (const item of data.content) {
          itemList.appendChild(renderItem(item));
        }
      }

      loadMoreWrap.hidden = state.page + 1 >= state.totalPages;
      setStatus(listStatus, "", null);
    } catch (err) {
      setStatus(listStatus, err.message, "error");
      if (!append) itemList.innerHTML = "";
    }
  }

  saveForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const url = urlInput.value.trim();
    if (!url) return;

    saveButton.disabled = true;
    setStatus(saveStatus, "Saving…", "info");
    try {
      const response = await fetch(API_ITEMS, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url, userNote: noteInput.value || null }),
      });
      if (response.status === 401) {
        throw new Error(
          "Not authenticated. Run the backend with DEV_AUTH_ENABLED=true locally, or " +
          "save items via the Telegram bot — there is no login UI yet."
        );
      }
      if (!response.ok) throw new Error(await parseErrorResponse(response));

      const created = response.status === 201;
      setStatus(saveStatus, created ? "Saved!" : "Already saved — showing the existing item.", "success");
      urlInput.value = "";
      noteInput.value = "";

      state.mode = "list";
      state.query = "";
      listTitle.textContent = "Your saved items";
      await loadPage(0);
    } catch (err) {
      setStatus(saveStatus, err.message, "error");
    } finally {
      saveButton.disabled = false;
    }
  });

  searchForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const query = searchInput.value.trim();
    if (!query) return;
    state.mode = "search";
    state.query = query;
    listTitle.textContent = `Search results for "${query}"`;
    await loadPage(0);
  });

  clearSearchButton.addEventListener("click", async () => {
    searchInput.value = "";
    state.mode = "list";
    state.query = "";
    listTitle.textContent = "Your saved items";
    await loadPage(0);
  });

  refreshButton.addEventListener("click", () => loadPage(0));
  loadMoreButton.addEventListener("click", () => loadPage(state.page + 1, { append: true }));

  loadPage(0);
})();
