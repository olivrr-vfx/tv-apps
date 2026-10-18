/* PurpleTV phone remote. Talks to the API of the TV it is served from. */
(function () {
  "use strict";

  var $ = function (sel) { return document.querySelector(sel); };
  var $$ = function (sel) { return Array.prototype.slice.call(document.querySelectorAll(sel)); };
  var STORE = "purpletv.code";

  // The code travels in the URL so "Add to Home Screen" keeps it (iOS gives the home screen app
  // its own storage); localStorage is the fallback for plain browser use.
  var params = new URLSearchParams(location.search);
  var code = params.get("code") || safeGet(STORE) || "";
  var state = null;
  var failures = 0;
  var pollTimer = null;
  var swipeMode = safeGet("purpletv.swipe") === "1";

  function safeGet(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
  function safeSet(k, v) { try { if (v == null) localStorage.removeItem(k); else localStorage.setItem(k, v); } catch (e) { /* private mode */ } }

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  function clock(sec) {
    sec = Math.max(0, Math.floor(sec || 0));
    var h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
    var mm = h ? String(m).padStart(2, "0") : String(m);
    return (h ? h + ":" : "") + mm + ":" + String(s).padStart(2, "0");
  }

  function count(n) {
    if (n == null || n < 0) return "";
    if (n >= 1e6) return (n / 1e6).toFixed(1).replace(".", ",") + " Mio.";
    if (n >= 1e4) return (n / 1e3).toFixed(1).replace(".", ",") + "K";
    return String(n);
  }

  var toastTimer = null;
  function toast(text) {
    var t = $("#toast");
    t.textContent = text;
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.hidden = true; }, 2200);
  }

  // --- API -------------------------------------------------------------------------------

  function api(method, path, query) {
    var qs = query ? "?" + new URLSearchParams(query).toString() : "";
    return fetch("/api/" + path + qs, {
      method: method,
      headers: { Authorization: "Bearer " + code },
      cache: "no-store",
    }).then(function (r) {
      if (r.status === 401) { showPairing("Der Code stimmt nicht mehr. Bitte neu eingeben."); throw new Error("auth"); }
      return r.json().catch(function () { return {}; }).then(function (body) {
        if (!r.ok) throw new Error(body.error || ("HTTP " + r.status));
        return body;
      });
    });
  }
  function post(path, query) {
    return api("POST", path, query).catch(function (e) {
      if (e.message !== "auth") toast(e.message === "Failed to fetch" ? "TV nicht erreichbar" : e.message);
      throw e;
    });
  }
  function send(path, query) { post(path, query).catch(function () {}); }

  // --- Pairing ---------------------------------------------------------------------------

  function showPairing(message) {
    clearInterval(pollTimer);
    $("#app").hidden = true;
    $("#pair").hidden = false;
    $("#pair-error").textContent = message || "";
    $("#pair-code").value = "";
  }

  function rememberCode(c) {
    code = c;
    safeSet(STORE, c);
    var url = new URL(location.href);
    url.searchParams.set("code", c);
    history.replaceState(null, "", url.toString());
    $("#manifest").href = "/manifest.webmanifest?code=" + encodeURIComponent(c);
  }

  $("#pair-form").addEventListener("submit", function (e) {
    e.preventDefault();
    var c = $("#pair-code").value.trim();
    if (!/^\d{6}$/.test(c)) { $("#pair-error").textContent = "Der Code hat 6 Ziffern."; return; }
    code = c;
    api("GET", "state").then(function (s) {
      rememberCode(c);
      start(s);
    }).catch(function (err) {
      if (err.message !== "auth") $("#pair-error").textContent = "TV nicht erreichbar. Ist PurpleTV offen?";
    });
  });

  $("#unpair").addEventListener("click", function () {
    safeSet(STORE, null);
    var url = new URL(location.href);
    url.searchParams.delete("code");
    history.replaceState(null, "", url.toString());
    code = "";
    showPairing();
  });

  // --- State polling ---------------------------------------------------------------------

  function start(initial) {
    $("#pair").hidden = true;
    $("#app").hidden = false;
    $("#info-host").textContent = location.host;
    if (initial) render(initial);
    clearInterval(pollTimer);
    pollTimer = setInterval(poll, 1500);
    poll();
    loadTab(currentTab);
  }

  function poll() {
    if (document.hidden) return;
    api("GET", "state").then(function (s) {
      failures = 0;
      render(s);
    }).catch(function (e) {
      if (e.message === "auth") return;
      failures++;
      setConn(failures > 1 ? "off" : "", failures > 1 ? "TV nicht erreichbar" : "Verbinde…");
    });
  }
  document.addEventListener("visibilitychange", function () { if (!document.hidden && code) poll(); });

  function setConn(cls, text) {
    var c = $("#conn");
    c.className = "conn " + cls;
    $("#conn-text").textContent = text;
  }

  var SCREEN_NAMES = { dashboard: "Start", twitch: "Twitch", youtube: "YouTube", settings: "Einstellungen", live: "Twitch live", vod: "Twitch-Video", video: "YouTube-Video", background: "TV-Startseite" };

  function render(s) {
    state = s;
    $("#info-version").textContent = s.version || "";
    setConn(s.screen === "background" ? "" : "ok", s.screen === "background" ? "PurpleTV im Hintergrund" : "Verbunden · " + (SCREEN_NAMES[s.screen] || s.screen));

    var m = s.media;
    var now = $("#now");
    now.classList.toggle("empty", !m);
    $("#now-bg").style.backgroundImage = m && m.thumbnail ? "url(" + JSON.stringify(m.thumbnail) + ")" : "none";
    var tag = $("#now-app");
    tag.textContent = m ? (m.app === "twitch" ? "Twitch" : "YouTube") : "";
    tag.className = "tag " + (m ? m.app : "");
    $("#now-live").hidden = !(m && m.live);
    $("#now-title").textContent = m ? (m.title || m.channel || "Lädt…") : "Nichts läuft gerade";
    var sub = m ? [m.channel, m.game, m.viewers ? count(m.viewers) + " Zuschauer" : ""].filter(Boolean).join(" · ") : "Starte etwas über Twitch oder YouTube.";
    $("#now-sub").textContent = sub;
    var hasTime = m && !m.live && m.duration > 0;
    $("#now-progress").hidden = !hasTime;
    $("#now-times").hidden = !hasTime;
    $("#seek-row").hidden = !hasTime;
    if (hasTime) {
      $("#now-bar").style.width = Math.min(100, (m.position / m.duration) * 100) + "%";
      $("#now-pos").textContent = clock(m.position);
      $("#now-dur").textContent = clock(m.duration);
    }
    $("#pp-icon").innerHTML = s.playing ? '<path d="M6 5h4v14H6zm8 0h4v14h-4z"/>' : '<path d="M8 5v14l11-7z"/>';
    $("#mute").classList.toggle("on", !!s.muted);
    $("#mute-path").setAttribute("d", s.muted
      ? "M16.5 12A4.5 4.5 0 0 0 14 8v2.2l2.4 2.4c.1-.2.1-.4.1-.6zM19 12c0 .9-.2 1.8-.5 2.6l1.5 1.5A8.9 8.9 0 0 0 21 12c0-4.3-3-7.9-7-8.8v2.1c2.9.9 5 3.5 5 6.7zM4.3 3 3 4.3 7.7 9H3v6h4l5 5v-6.7l4.3 4.2c-.7.5-1.4.9-2.3 1.2v2.1a9 9 0 0 0 3.7-1.8l2 2 1.3-1.3-9-9L4.3 3zM12 4 9.9 6.1 12 8.2V4z"
      : "M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 8v8a4.5 4.5 0 0 0 2.5-4z");
    if (!volumeDragging) $("#volume").value = Math.round((s.volume || 0) * 100);
    $$(".app-tile").forEach(function (b) {
      var name = b.getAttribute("data-app");
      b.hidden = name !== "dashboard" && (s.apps || []).indexOf(name) < 0;
    });
    $$(".tabbar button").forEach(function (b) {
      var t = b.getAttribute("data-tab");
      if (t === "twitch" || t === "youtube") b.hidden = (s.apps || []).indexOf(t) < 0;
    });
    if (currentTab === "twitch") renderTwitch(s.followed_live || []);
  }

  // --- Remote ----------------------------------------------------------------------------

  $$("[data-key]").forEach(function (b) {
    b.addEventListener("click", function () { send("key", { code: b.getAttribute("data-key") }); });
  });
  $$("[data-cmd]").forEach(function (b) {
    b.addEventListener("click", function () { send(b.getAttribute("data-cmd")); });
  });
  $$("[data-seek]").forEach(function (b) {
    b.addEventListener("click", function () { send("seek", { delta: b.getAttribute("data-seek") }); });
  });
  $("#mute").addEventListener("click", function () { send("mute", { mute: !(state && state.muted) }); });

  var volumeDragging = false;
  var volumeTimer = null;
  var vol = $("#volume");
  vol.addEventListener("input", function () {
    volumeDragging = true;
    clearTimeout(volumeTimer);
    volumeTimer = setTimeout(function () {
      send("volume", { level: (vol.value / 100).toFixed(2) });
      volumeDragging = false;
    }, 120);
  });

  function applyPadMode() {
    $("#dpad").hidden = swipeMode;
    $("#swipe").hidden = !swipeMode;
    $("#pad-mode").classList.toggle("on", swipeMode);
    $("#pad-mode").textContent = swipeMode ? "Tasten" : "Wischfeld";
  }
  $("#pad-mode").addEventListener("click", function () {
    swipeMode = !swipeMode;
    safeSet("purpletv.swipe", swipeMode ? "1" : "0");
    applyPadMode();
  });
  applyPadMode();

  // Swipe pad: a swipe moves one step, tap = OK.
  (function () {
    var pad = $("#swipe"), sx = 0, sy = 0, st = 0;
    pad.addEventListener("touchstart", function (e) {
      var t = e.touches[0]; sx = t.clientX; sy = t.clientY; st = Date.now();
    }, { passive: true });
    pad.addEventListener("touchend", function (e) {
      var t = e.changedTouches[0], dx = t.clientX - sx, dy = t.clientY - sy;
      var key;
      if (Math.abs(dx) < 18 && Math.abs(dy) < 18 && Date.now() - st < 400) key = "ok";
      else if (Math.abs(dx) > Math.abs(dy)) key = dx > 0 ? "right" : "left";
      else key = dy > 0 ? "down" : "up";
      pad.classList.add("flash");
      setTimeout(function () { pad.classList.remove("flash"); }, 120);
      send("key", { code: key });
      e.preventDefault();
    });
  })();

  // --- Tabs ------------------------------------------------------------------------------

  var currentTab = safeGet("purpletv.tab") || "remote";
  function showTab(tab) {
    currentTab = tab;
    safeSet("purpletv.tab", tab);
    $$(".tabbar button").forEach(function (b) { b.classList.toggle("active", b.getAttribute("data-tab") === tab); });
    $$(".panel").forEach(function (p) { p.hidden = p.getAttribute("data-panel") !== tab; });
    loadTab(tab);
    window.scrollTo(0, 0);
  }
  $$(".tabbar button").forEach(function (b) {
    b.addEventListener("click", function () { showTab(b.getAttribute("data-tab")); });
  });

  function loadTab(tab) {
    if (tab === "twitch" && state) renderTwitch(state.followed_live || []);
    if (tab === "youtube") loadFeed();
  }

  // --- Twitch ----------------------------------------------------------------------------

  var lastTwitchKey = "";
  function renderTwitch(list) {
    var key = list.map(function (c) { return c.login + c.viewers; }).join();
    if (key === lastTwitchKey) return;
    lastTwitchKey = key;
    $("#tw-count").textContent = list.length ? String(list.length) : "";
    var box = $("#tw-list");
    if (!list.length) {
      box.innerHTML = '<p class="empty-note">Keiner deiner Kanäle ist live – oder du bist auf dem TV nicht bei Twitch angemeldet.</p>';
      return;
    }
    box.innerHTML = list.map(function (c) {
      return '<button class="item" data-login="' + esc(c.login) + '">' +
        '<span class="avatar live" style="background-image:url(' + esc(JSON.stringify(c.avatar || "")) + ')"></span>' +
        '<span class="text"><span class="title">' + esc(c.display_name) + '</span>' +
        '<span class="sub">' + esc(c.title) + '</span>' +
        '<span class="meta">' + esc([c.game, count(c.viewers) + " Zuschauer"].filter(Boolean).join(" · ")) + '</span></span></button>';
    }).join("");
  }
  $("#tw-list").addEventListener("click", function (e) {
    var item = e.target.closest("[data-login]");
    if (!item) return;
    post("open", { login: item.getAttribute("data-login") }).then(function () { toast("Läuft auf dem TV"); }).catch(function () {});
  });
  $("#tw-open").addEventListener("submit", function (e) {
    e.preventDefault();
    var login = $("#tw-login").value.trim();
    if (!login) return;
    post("open", { login: login }).then(function () { toast(login + " wird geöffnet"); $("#tw-login").blur(); }).catch(function () {});
  });

  // --- YouTube ---------------------------------------------------------------------------

  function videoItem(v) {
    var thumb = '<span class="thumb" style="background-image:url(' + esc(JSON.stringify(v.thumbnail || "")) + ')">' +
      (v.live ? '<span class="lv">LIVE</span>' : (v.duration ? '<span class="dur">' + clock(v.duration) + '</span>' : "")) + '</span>';
    return '<button class="item" data-video="' + esc(v.id) + '">' + thumb +
      '<span class="text"><span class="title">' + esc(v.title) + '</span>' +
      '<span class="sub">' + esc([v.channel, v.uploaded].filter(Boolean).join(" · ")) + '</span></span></button>';
  }

  function ytView(view) {
    $$("[data-yt]").forEach(function (c) { c.classList.toggle("active", c.getAttribute("data-yt") === view); });
    $$("[data-yt-view]").forEach(function (v) { v.hidden = v.getAttribute("data-yt-view") !== view; });
    if (view === "feed") loadFeed();
    if (view === "manage") loadSubs();
  }
  $$("[data-yt]").forEach(function (c) { c.addEventListener("click", function () { ytView(c.getAttribute("data-yt")); }); });

  function loadFeed() {
    api("GET", "youtube/feed").then(function (r) {
      var videos = r.videos || [];
      $("#yt-feed").innerHTML = videos.length ? videos.map(videoItem).join("")
        : '<p class="empty-note">Noch keine Abos. Unter „Kanäle verwalten“ oder über die Suche hinzufügen.</p>';
    }).catch(function () {});
  }

  function playVideo(e) {
    var item = e.target.closest("[data-video]");
    if (!item) return;
    post("youtube", { id: item.getAttribute("data-video") }).then(function () { toast("Läuft auf dem TV"); }).catch(function () {});
  }
  $("#yt-feed").addEventListener("click", playVideo);
  $("#yt-videos").addEventListener("click", playVideo);

  $("#yt-search").addEventListener("submit", function (e) {
    e.preventDefault();
    var q = $("#yt-query").value.trim();
    if (!q) return;
    $("#yt-query").blur();
    ytView("results");
    $("#yt-videos").innerHTML = '<p class="empty-note">Suche…</p>';
    $("#yt-channels").innerHTML = "";
    api("GET", "youtube/search", { q: q }).then(function (r) {
      var subs = {};
      (r.subscribed || []).forEach(function (id) { subs[id] = true; });
      $("#yt-channels").innerHTML = (r.channels || []).map(function (c) {
        return '<div class="channel"><span class="avatar" style="background-image:url(' + esc(JSON.stringify(c.avatar || "")) + ')"></span>' +
          '<span>' + esc(c.name) + '</span><button class="sub-btn" data-sub="' + esc(c.url) + '">Abonnieren</button></div>';
      }).join("");
      $("#yt-videos").innerHTML = (r.videos || []).map(videoItem).join("") || '<p class="empty-note">Nichts gefunden.</p>';
    }).catch(function (err) {
      $("#yt-videos").innerHTML = '<p class="empty-note">' + esc(err.message) + '</p>';
    });
  });
  $("#yt-show-tv").addEventListener("click", function () {
    var q = $("#yt-query").value.trim();
    if (q) post("youtube/search", { q: q }).then(function () { toast("Suche auf dem TV geöffnet"); }).catch(function () {});
  });
  $("#yt-channels").addEventListener("click", function (e) {
    var b = e.target.closest("[data-sub]");
    if (!b || b.classList.contains("done")) return;
    b.textContent = "…";
    post("youtube/subscribe", { url: b.getAttribute("data-sub") }).then(function (r) {
      b.textContent = "Abonniert";
      b.classList.add("done");
      toast((r.channel && r.channel.name ? r.channel.name : "Kanal") + " abonniert");
    }).catch(function () { b.textContent = "Abonnieren"; });
  });

  function loadSubs() {
    api("GET", "youtube/subscriptions").then(function (r) {
      var list = r.channels || [];
      $("#yt-sub-count").textContent = list.length ? String(list.length) : "";
      $("#yt-subs").innerHTML = list.map(function (c) {
        return '<div class="item"><span class="avatar" style="background-image:url(' + esc(JSON.stringify(c.avatar || "")) + ')"></span>' +
          '<span class="text"><span class="title">' + esc(c.name) + '</span></span>' +
          '<button class="btn ghost action" data-unsub="' + esc(c.id) + '">Entfernen</button></div>';
      }).join("") || '<p class="empty-note">Noch keine Abos.</p>';
    }).catch(function () {});
  }
  $("#yt-subs").addEventListener("click", function (e) {
    var b = e.target.closest("[data-unsub]");
    if (!b) return;
    post("youtube/unsubscribe", { id: b.getAttribute("data-unsub") }).then(loadSubs).catch(function () {});
  });
  $("#yt-add-btn").addEventListener("click", function () {
    var lines = $("#yt-add").value.split(/\n|,/).map(function (s) { return s.trim(); }).filter(Boolean);
    if (!lines.length) return;
    var status = $("#yt-add-status");
    var done = 0, failed = [];
    status.textContent = "Füge " + lines.length + " Kanäle hinzu…";
    lines.reduce(function (p, line) {
      return p.then(function () {
        return api("POST", "youtube/subscribe", { url: line }).then(function () { done++; }).catch(function () { failed.push(line); });
      });
    }, Promise.resolve()).then(function () {
      status.textContent = done + " abonniert" + (failed.length ? " · nicht gefunden: " + failed.join(", ") : "");
      $("#yt-add").value = failed.join("\n");
      loadSubs();
    });
  });

  // --- TV tab ----------------------------------------------------------------------------

  $$(".app-tile").forEach(function (b) {
    b.addEventListener("click", function () {
      post("app", { name: b.getAttribute("data-app") }).then(function () { toast(b.textContent.trim() + " geöffnet"); }).catch(function () {});
    });
  });

  // --- Boot ------------------------------------------------------------------------------

  showTab(currentTab);
  if (!code) {
    showPairing();
  } else {
    api("GET", "state").then(function (s) {
      rememberCode(code);
      start(s);
    }).catch(function (e) {
      if (e.message !== "auth") {
        // TV unreachable right now: show the app anyway and keep polling.
        start(null);
        setConn("off", "TV nicht erreichbar");
      }
    });
  }
})();
