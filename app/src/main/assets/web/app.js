/* Couch web app for phones and computers. Talks to the API of the TV it is served from. */
(function () {
  "use strict";

  var $ = function (sel) { return document.querySelector(sel); };
  var $$ = function (sel) { return Array.prototype.slice.call(document.querySelectorAll(sel)); };
  var STORE = "couch.code";

  var params = new URLSearchParams(location.search);
  // Demo: opened as a file or with ?demo=1 – sample data, no TV needed.
  var DEMO = window.COUCH_DEMO === true || params.get("demo") === "1" || location.protocol === "file:" || /claudeusercontent\.com$|github\.io$/.test(location.hostname);
  // The code travels in the URL so "Add to Home Screen" keeps it (iOS gives the home screen app
  // its own storage); localStorage is the fallback for plain browser use.
  var code = params.get("code") || safeGet(STORE) || safeGet("purpletv.code") || (DEMO ? "123456" : "");
  var state = null;
  var failures = 0;
  var pollTimer = null;
  var swipeMode = safeGet("couch.swipe") === "1";
  var isDesktop = function () { return window.matchMedia("(min-width: 980px)").matches; };
  var HTTP_PORT = 8765;

  function safeGet(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
  function safeSet(k, v) { try { if (v == null) localStorage.removeItem(k); else localStorage.setItem(k, v); } catch (e) { /* private mode */ } }

  // --- Helpers ---------------------------------------------------------------------------

  function el(tag, attrs, children) {
    var e = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (k) {
      var v = attrs[k];
      if (v == null || v === false) return;
      if (k === "text") e.textContent = v;
      else if (k === "style") e.setAttribute("style", v);
      else if (k.indexOf("on") === 0) e.addEventListener(k.slice(2), v);
      else e.setAttribute(k, v === true ? "" : v);
    });
    (children || []).forEach(function (c) { if (c != null) e.appendChild(typeof c === "string" ? document.createTextNode(c) : c); });
    return e;
  }
  function bg(url) { return url ? "background-image:url(" + JSON.stringify(url) + ")" : null; }

  function clock(sec) {
    sec = Math.max(0, Math.floor(sec || 0));
    var h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
    return (h ? h + ":" + String(m).padStart(2, "0") : String(m)) + ":" + String(s).padStart(2, "0");
  }
  function count(n) {
    if (n == null || n < 0) return "";
    if (n >= 1e6) return (n / 1e6).toFixed(1).replace(".", ",") + " Mio.";
    if (n >= 1e4) return (n / 1e3).toFixed(1).replace(".", ",") + "K";
    return String(n);
  }
  function ago(ms) {
    if (!ms) return "";
    var min = (Date.now() - ms) / 60000;
    if (min < 60) return "vor " + Math.max(1, Math.round(min)) + " Min.";
    if (min < 1440) return "vor " + Math.round(min / 60) + " Std.";
    if (min < 2880) return "gestern";
    return "vor " + Math.round(min / 1440) + " Tagen";
  }
  function deviceName() {
    var ua = navigator.userAgent;
    if (/iPhone/.test(ua)) return "iPhone";
    if (/iPad/.test(ua)) return "iPad";
    if (/Macintosh/.test(ua)) return "Mac";
    if (/Android/.test(ua)) return "Android";
    if (/Windows/.test(ua)) return "PC";
    return "Browser";
  }

  var toastTimer = null;
  function toast(text) {
    var t = $("#toast");
    t.textContent = text;
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.hidden = true; }, 2400);
  }

  function copy(text) {
    var done = function () { toast("Kopiert"); };
    if (navigator.clipboard && window.isSecureContext) return navigator.clipboard.writeText(text).then(done);
    var ta = el("textarea", { style: "position:fixed;opacity:0" });
    ta.value = text;
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand("copy"); done(); } catch (e) { prompt("Link kopieren:", text); }
    ta.remove();
  }
  function shareLink(title, url) {
    if (navigator.share) {
      navigator.share({ title: title, url: url }).catch(function () {});
    } else {
      copy(url);
    }
  }

  // --- API -------------------------------------------------------------------------------

  function api(method, path, query, body) {
    if (DEMO) return demoApi(method, path, query);
    var qs = query ? "?" + new URLSearchParams(query).toString() : "";
    return fetch("/api/" + path + qs, {
      method: method,
      headers: { Authorization: "Bearer " + code, "Content-Type": "text/plain" },
      body: body ? JSON.stringify(body) : undefined,
      cache: "no-store",
    }).then(function (r) {
      if (r.status === 401) { showPairing("Der Code stimmt nicht mehr. Bitte neu eingeben."); throw new Error("auth"); }
      return r.json().catch(function () { return {}; }).then(function (b) {
        if (!r.ok) throw new Error(b.error || ("HTTP " + r.status));
        return b;
      });
    });
  }
  function post(path, query, body) {
    return api("POST", path, query, body).catch(function (e) {
      if (e.message !== "auth") toast(e.message === "Failed to fetch" || e.message === "Load failed" ? "TV nicht erreichbar" : e.message);
      throw e;
    });
  }
  function send(path, query) { return post(path, query).catch(function () {}); }

  // --- Items -----------------------------------------------------------------------------

  function twitchItem(c) {
    var live = c.live !== false;
    return {
      app: "twitch", kind: live ? "live" : "channel", id: c.login, title: c.display_name || c.login, sub: c.title || "",
      meta: [c.game, c.viewers ? count(c.viewers) + " Zuschauer" : ""].filter(Boolean).join(" · "),
      thumb: live ? c.thumbnail : "", avatar: c.avatar, live: live, url: "https://www.twitch.tv/" + c.login,
    };
  }
  function ytItem(v) {
    return {
      app: "youtube", kind: "video", id: v.id, title: v.title, sub: v.channel || "",
      meta: v.uploaded || ago(v.uploaded_ms), thumb: v.thumbnail, live: !!v.live, duration: v.duration,
      url: "https://youtu.be/" + v.id,
    };
  }
  function historyItem(h) {
    if (h.app === "twitch") {
      return { app: "twitch", kind: "vod", id: h.id, title: h.title, sub: h.channel, meta: "Weiter bei " + Math.round(h.progress * 100) + " %",
        thumb: h.thumbnail, progress: h.progress, duration: h.duration, url: "https://www.twitch.tv/videos/" + h.id };
    }
    return { app: "youtube", kind: "video", id: h.id, title: h.title, sub: h.channel, meta: "Weiter bei " + clock(h.position),
      thumb: h.thumbnail, progress: h.progress, duration: h.duration, position: h.position, url: "https://youtu.be/" + h.id + (h.position ? "?t=" + h.position : "") };
  }
  function mediaItem(m) {
    if (!m) return null;
    if (m.app === "youtube") return { app: "youtube", kind: "video", id: m.id, title: m.title, position: m.position, live: m.live, url: "https://youtu.be/" + m.id + (m.position && !m.live ? "?t=" + m.position : "") };
    if (m.kind === "vod") return { app: "twitch", kind: "vod", id: m.id, title: m.title, url: "https://www.twitch.tv/videos/" + m.id };
    return { app: "twitch", kind: "live", id: m.channel_id || m.id, title: m.channel || m.title, live: true, url: "https://www.twitch.tv/" + (m.channel_id || m.id) };
  }

  function playOnTv(item) {
    var p;
    if (item.app === "youtube") p = post("youtube", { id: item.id });
    else if (item.kind === "vod") p = post("vod", { id: item.id });
    else if (item.kind === "channel") p = post("channel", { login: item.id });
    else p = post("open", { login: item.id });
    p.then(function () { toast("▶ Läuft auf dem TV"); }).catch(function () {});
  }

  function card(item) {
    var pic = el("div", { "class": "pic", style: bg(item.thumb) }, [
      item.live ? el("span", { "class": "b tl live-b", text: "LIVE" }) : null,
      item.kind === "channel" ? el("span", { "class": "b tl", text: "Offline" }) : null,
      item.duration && !item.live ? el("span", { "class": "b br", text: clock(item.duration) }) : null,
      item.progress ? el("div", { "class": "bar" }, [el("div", { style: "width:" + Math.round(item.progress * 100) + "%" })]) : null,
    ]);
    var acts = el("div", { "class": "acts" }, [
      el("button", { "class": "tv", text: "▶ TV", title: "Auf dem TV abspielen", onclick: function (e) { e.stopPropagation(); playOnTv(item); } }),
      item.kind !== "channel" ? el("button", { text: "Hier ansehen", onclick: function (e) { e.stopPropagation(); watchHere(item); } }) : null,
      el("button", { text: "Teilen", title: "Link kopieren", onclick: function (e) { e.stopPropagation(); shareLink(item.title, item.url); } }),
    ]);
    var more = el("button", { "class": "more", "aria-label": "Mehr", onclick: function (e) { e.stopPropagation(); openSheet(item); } });
    more.innerHTML = '<svg viewBox="0 0 24 24"><path d="M6 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm6 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm6 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4z"/></svg>';
    var text = el("div", {}, [
      el("div", { "class": "t", text: item.title }),
      item.sub ? el("div", { "class": "s", text: item.sub }) : null,
      item.meta ? el("div", { "class": "m", text: item.meta }) : null,
    ]);
    var txt = el("div", { "class": "txt" }, [item.avatar ? el("span", { "class": "avatar", style: bg(item.avatar) }) : null, text]);
    return el("div", {
      "class": "card" + (item.app === "youtube" ? " yt" : ""), tabindex: "0", role: "button",
      title: "Klick: auf dem TV abspielen",
      onclick: function () { playOnTv(item); },
      onkeydown: function (e) { if (e.key === "Enter") playOnTv(item); },
    }, [pic, acts, more, txt]);
  }

  var lastKeys = {};
  function renderCards(target, items, empty) {
    var box = typeof target === "string" ? $(target) : target;
    var key = items.map(function (i) { return i.id + "|" + (i.meta || "") + "|" + (i.progress || ""); }).join();
    if (lastKeys[box.id] === key && box.childElementCount) return;
    lastKeys[box.id] = key;
    box.innerHTML = "";
    if (!items.length) {
      if (empty) box.appendChild(el("p", { "class": "empty-note", text: empty }));
      return;
    }
    items.forEach(function (i) { box.appendChild(card(i)); });
  }

  // --- Sheet (phones) --------------------------------------------------------------------

  var sheetItem = null;
  function openSheet(item) {
    sheetItem = item;
    $("#sheet-title").textContent = item.title;
    $("[data-sheet=here]").hidden = item.kind === "channel";
    $("#sheet").hidden = false;
  }
  $("#sheet").addEventListener("click", function (e) {
    var b = e.target.closest("[data-sheet]");
    if (!b && e.target !== $("#sheet")) return;
    var what = b ? b.getAttribute("data-sheet") : "cancel";
    $("#sheet").hidden = true;
    if (!sheetItem) return;
    if (what === "tv") playOnTv(sheetItem);
    if (what === "here") watchHere(sheetItem);
    if (what === "share") shareLink(sheetItem.title, sheetItem.url);
  });

  // --- Browser player --------------------------------------------------------------------

  var hls = null, playerItem = null;
  function watchHere(item) {
    playerItem = item;
    var stage = $("#player-stage");
    closeStage();
    $("#player").hidden = false;
    $("#player-title").textContent = item.title || "";
    var tag = $("#player-app");
    tag.textContent = item.app === "youtube" ? "YouTube" : "Twitch";
    tag.className = "tag " + item.app;
    $("#player-note").textContent = "";
    if (DEMO) {
      $("#player-note").textContent = "Im Demo-Modus gibt es keine Wiedergabe – mit dem TV verbunden läuft das Video hier.";
      return;
    }
    if (item.app === "youtube") {
      var start = item.position && !item.live ? "&start=" + item.position : "";
      stage.appendChild(el("iframe", {
        src: "https://www.youtube-nocookie.com/embed/" + encodeURIComponent(item.id) + "?autoplay=1&rel=0&playsinline=1" + start,
        allow: "autoplay; encrypted-media; picture-in-picture; fullscreen", allowfullscreen: true,
        referrerpolicy: "strict-origin-when-cross-origin",
      }));
      $("#player-note").textContent = "Läuft über den YouTube-Player. Manche Videos erlauben das nicht – dann „Auf dem TV“.";
      return;
    }
    var src = (item.kind === "vod" ? "/play/twitch/vod/" : "/play/twitch/") + encodeURIComponent(item.id) + ".m3u8?token=" + encodeURIComponent(code);
    var video = el("video", { controls: true, autoplay: true, playsinline: true });
    stage.appendChild(video);
    var fail = function () { $("#player-note").textContent = item.kind === "vod" ? "Video nicht verfügbar." : "Stream nicht verfügbar – ist der Kanal offline?"; };
    if (window.Hls && window.Hls.isSupported()) {
      hls = new window.Hls({ enableWorker: true, lowLatencyMode: false, liveSyncDuration: 6 });
      hls.loadSource(src);
      hls.attachMedia(video);
      hls.on(window.Hls.Events.ERROR, function (_e, d) { if (d.fatal) fail(); });
    } else if (video.canPlayType("application/vnd.apple.mpegurl")) {
      video.src = src;
      video.addEventListener("error", fail);
    } else {
      fail();
    }
    video.play().catch(function () {});
    $("#player-note").textContent = "Kommt über deinen TV, Werbung wird wie dort gefiltert.";
  }
  function closeStage() {
    if (hls) { hls.destroy(); hls = null; }
    $("#player-stage").innerHTML = "";
  }
  function closePlayer() {
    closeStage();
    $("#player").hidden = true;
  }
  $("#player-close").addEventListener("click", closePlayer);
  $("#player").addEventListener("click", function (e) { if (e.target === $("#player")) closePlayer(); });
  $("#player-tv").addEventListener("click", function () { if (playerItem) { playOnTv(playerItem); closePlayer(); } });
  $("#player-share").addEventListener("click", function () { if (playerItem) shareLink(playerItem.title, playerItem.url); });

  // --- Pairing ---------------------------------------------------------------------------

  function showPairing(message) {
    clearInterval(pollTimer);
    $("#app").hidden = true;
    $("#send").hidden = true;
    $("#pair").hidden = false;
    $("#pair-error").textContent = message || "";
    $("#pair-code").value = "";
  }

  function rememberCode(c) {
    code = c;
    safeSet(STORE, c);
    if (DEMO) return;
    var url = new URL(location.href);
    url.searchParams.set("code", c);
    history.replaceState(null, "", url.toString());
  }

  $("#pair-form").addEventListener("submit", function (e) {
    e.preventDefault();
    var c = $("#pair-code").value.trim();
    if (!/^\d{6}$/.test(c)) { $("#pair-error").textContent = "Der Code hat 6 Ziffern."; return; }
    code = c;
    api("GET", "state").then(function (s) {
      rememberCode(c);
      if (location.pathname === "/send") return sendFlow();
      start(s);
    }).catch(function (err) {
      if (err.message !== "auth") $("#pair-error").textContent = "TV nicht erreichbar. Ist Couch auf dem TV offen und bist du im selben WLAN?";
    });
  });

  // --- /send: a link from the bookmarklet or iPhone shortcut -----------------------------

  function sendFlow() {
    $("#pair").hidden = true;
    $("#app").hidden = true;
    $("#send").hidden = false;
    var url = params.get("url") || "";
    if (!url) {
      $("#send-title").textContent = "Kein Link";
      $("#send-text").textContent = "Diese Seite bekommt ihren Link vom Lesezeichen „Auf Couch abspielen“.";
      return;
    }
    $("#send-text").textContent = url;
    api("POST", "open", { url: url }).then(function () {
      $("#send-title").textContent = "▶ Läuft auf dem TV";
      setTimeout(function () { window.close(); }, 1800);
    }).catch(function (e) {
      if (e.message === "auth") return;
      $("#send-title").textContent = "Das hat nicht geklappt";
      $("#send-text").textContent = e.message;
    });
  }
  $("#send-close").addEventListener("click", function () { window.close(); });

  // --- State polling ---------------------------------------------------------------------

  function start(initial) {
    $("#pair").hidden = true;
    $("#send").hidden = true;
    $("#app").hidden = false;
    setupShare();
    if (initial) render(initial);
    clearInterval(pollTimer);
    pollTimer = setInterval(poll, 1500);
    poll();
    route();
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
    $("#conn").className = "conn " + cls;
    $("#conn-text").textContent = text;
  }

  var SCREEN_NAMES = { dashboard: "Start", twitch: "PurpleTV", youtube: "YouTube", settings: "Einstellungen", live: "Twitch live", vod: "Twitch-Video", video: "YouTube", screen: "Bildschirm geteilt", media: "Fotos", background: "im Hintergrund" };

  function render(s) {
    state = s;
    setConn(s.screen === "background" ? "" : "ok", s.screen === "background" ? "Couch im Hintergrund" : "Verbunden · " + (SCREEN_NAMES[s.screen] || s.screen));
    var m = s.media;
    document.title = m ? "▶ " + (m.title || m.channel) + " – Couch" : "Couch";

    // Dock
    $("#now").classList.toggle("empty", !m);
    $("#now-bg").setAttribute("style", bg(m && m.thumbnail) || "");
    var tag = $("#now-app");
    tag.textContent = m ? (m.app === "twitch" ? "Twitch" : "YouTube") : "";
    tag.className = "tag " + (m ? m.app : "");
    $("#now-live").hidden = !(m && m.live);
    $("#now-title").textContent = m ? (m.title || m.channel || "Lädt…") : "Nichts läuft gerade";
    var sub = m ? [m.channel, m.game, m.viewers ? count(m.viewers) + " Zuschauer" : ""].filter(Boolean).join(" · ") : "Starte etwas über PurpleTV oder YouTube.";
    $("#now-sub").textContent = sub;
    var hasTime = m && !m.live && m.duration > 0;
    ["#now-progress", "#now-times", "#seek-row"].forEach(function (id) { $(id).hidden = !hasTime; });
    if (hasTime) {
      $("#now-bar").style.width = Math.min(100, (m.position / m.duration) * 100) + "%";
      $("#now-pos").textContent = clock(m.position);
      $("#now-dur").textContent = clock(m.duration);
    }
    $("#pp-icon").innerHTML = s.playing ? '<path d="M6 5h4v14H6zm8 0h4v14h-4z"/>' : '<path d="M8 5v14l11-7z"/>';
    $("#mute-path").setAttribute("d", s.muted
      ? "M16.5 12A4.5 4.5 0 0 0 14 8v2.2l2.4 2.4c.1-.2.1-.4.1-.6zM19 12c0 .9-.2 1.8-.5 2.6l1.5 1.5A8.9 8.9 0 0 0 21 12c0-4.3-3-7.9-7-8.8v2.1c2.9.9 5 3.5 5 6.7zM4.3 3 3 4.3 7.7 9H3v6h4l5 5v-6.7l4.3 4.2c-.7.5-1.4.9-2.3 1.2v2.1a9 9 0 0 0 3.7-1.8l2 2 1.3-1.3-9-9L4.3 3zM12 4 9.9 6.1 12 8.2V4z"
      : "M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 8v8a4.5 4.5 0 0 0 2.5-4z");
    if (!volumeDragging) $("#volume").value = Math.round((s.volume || 0) * 100);

    // Hero on the start page
    $("#hero").classList.toggle("empty", !m);
    $("#hero-bg").setAttribute("style", bg(m && m.thumbnail) || "");
    $("#hero-app").textContent = tag.textContent;
    $("#hero-app").className = tag.className;
    $("#hero-live").hidden = !(m && m.live);
    $("#hero-title").textContent = m ? (m.title || m.channel) : "Gerade läuft nichts";
    $("#hero-sub").textContent = m ? sub : "Such dir unten etwas aus – ein Klick startet es auf dem TV.";
    $("#hero-progress").hidden = !hasTime;
    if (hasTime) $("#hero-bar").style.width = Math.min(100, (m.position / m.duration) * 100) + "%";
    $("#hero-actions").hidden = !m;

    // Apps that are switched off on the TV disappear here too.
    var apps = s.apps || ["twitch", "youtube"];
    $$("[data-app]").forEach(function (b) { b.hidden = apps.indexOf(b.getAttribute("data-app")) < 0; });

    var live = (s.followed_live || []).map(twitchItem);
    $("#start-live-count").textContent = live.length ? String(live.length) : "";
    $("#tw-count").textContent = live.length ? String(live.length) : "";
    // Nobody followed is live (or no login on the TV): show what is popular instead of nothing.
    var top = twitchTop || [];
    var showTop = !live.length && top.length > 0;
    $("#start-live-title").textContent = showTop ? "Beliebt auf Twitch" : "Live auf Twitch";
    renderCards("#start-live", showTop ? top.slice(0, 20) : live, "Keiner deiner Kanäle ist live.");
    renderCards("#tw-live", live, "Keiner deiner Kanäle ist gerade live.");
    // No subscriptions yet: trending videos instead of an empty row.
    var feed = (s.youtube_feed || []).slice(0, 20).map(ytItem);
    var trends = !feed.length && (ytTrends || []).length > 0;
    $("#start-yt-title").textContent = trends ? "Trends auf YouTube" : "Neu auf YouTube";
    renderCards("#start-yt", trends ? ytTrends.slice(0, 20) : feed, "Noch keine YouTube-Abos. Unter YouTube → Kanäle verwalten hinzufügen.");
  }

  $("#hero-watch").addEventListener("click", function () { var i = mediaItem(state && state.media); if (i) watchHere(i); });
  $("#hero-share").addEventListener("click", function () { var i = mediaItem(state && state.media); if (i) shareLink(i.title, i.url); });
  $$("[data-app-open]").forEach(function (b) {
    b.addEventListener("click", function () {
      post("app", { name: b.getAttribute("data-app-open") }).then(function () { toast(b.textContent.replace("TV: ", "") + " auf dem TV geöffnet"); }).catch(function () {});
    });
  });

  // --- Remote ----------------------------------------------------------------------------

  function key(name) {
    var b = $(".d." + name) || $("[data-key=" + name + "]");
    if (b) { b.classList.add("flash"); setTimeout(function () { b.classList.remove("flash"); }, 140); }
    if (navigator.vibrate) navigator.vibrate(8);
    send("key", { code: name });
  }
  $$("[data-key]").forEach(function (b) { b.addEventListener("click", function () { key(b.getAttribute("data-key")); }); });
  $$("[data-cmd]").forEach(function (b) { b.addEventListener("click", function () { send(b.getAttribute("data-cmd")); }); });
  $$("[data-seek]").forEach(function (b) { b.addEventListener("click", function () { send("seek", { delta: b.getAttribute("data-seek") }); }); });
  $("#mute").addEventListener("click", function () { send("mute", { mute: !(state && state.muted) }); });

  var volumeDragging = false, volumeTimer = null, vol = $("#volume");
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
    safeSet("couch.swipe", swipeMode ? "1" : "0");
    applyPadMode();
  });
  applyPadMode();

  // Swipe pad: a swipe moves one step, tap = OK.
  (function () {
    var pad = $("#swipe"), sx = 0, sy = 0, st = 0;
    pad.addEventListener("touchstart", function (e) { var t = e.touches[0]; sx = t.clientX; sy = t.clientY; st = Date.now(); }, { passive: true });
    pad.addEventListener("touchend", function (e) {
      var t = e.changedTouches[0], dx = t.clientX - sx, dy = t.clientY - sy, k;
      if (Math.abs(dx) < 18 && Math.abs(dy) < 18 && Date.now() - st < 400) k = "ok";
      else if (Math.abs(dx) > Math.abs(dy)) k = dx > 0 ? "right" : "left";
      else k = dy > 0 ? "down" : "up";
      pad.classList.add("flash");
      setTimeout(function () { pad.classList.remove("flash"); }, 120);
      key(k);
      e.preventDefault();
    });
  })();

  // Computer keyboard = TV remote (when not typing).
  document.addEventListener("keydown", function (e) {
    if (e.metaKey || e.ctrlKey || e.altKey) return;
    var t = e.target;
    if (t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA" || t.isContentEditable)) return;
    if (!$("#player").hidden) { if (e.key === "Escape") closePlayer(); return; }
    if ($("#app").hidden) return;
    var map = { ArrowUp: "up", ArrowDown: "down", ArrowLeft: "left", ArrowRight: "right", Enter: "ok", Escape: "back", Backspace: "back" };
    if (map[e.key] && !(e.key === "Enter" && t.closest && t.closest(".card, button, a"))) {
      e.preventDefault();
      key(map[e.key]);
    } else if (e.key === " ") {
      e.preventDefault();
      send("toggle");
    } else if (e.key === "m") {
      send("mute", { mute: !(state && state.muted) });
    } else if (e.key === "h") {
      send("home");
    }
  });

  // --- Navigation ------------------------------------------------------------------------

  var VIEWS = ["start", "twitch", "youtube", "share", "remote"];
  function route() {
    var view = (location.hash || "#start").slice(1);
    if (VIEWS.indexOf(view) < 0) view = "start";
    if (view === "remote" && isDesktop()) view = "start";
    document.body.className = "v-" + view;
    $$(".view").forEach(function (v) { v.hidden = v.getAttribute("data-view") !== view; });
    $$("[data-nav]").forEach(function (a) { a.classList.toggle("active", a.getAttribute("data-nav") === view); });
    $$(".tabbar button").forEach(function (b) { b.classList.toggle("active", b.getAttribute("data-tab") === view); });
    loadView(view);
  }
  window.addEventListener("hashchange", function () { route(); window.scrollTo(0, 0); });
  $$(".tabbar button").forEach(function (b) {
    b.addEventListener("click", function () { location.hash = b.getAttribute("data-tab"); });
  });

  function loadView(view) {
    if (view === "start") { loadHistory(); loadTwitchTop(); loadYtTrendsForStart(); }
    if (view === "twitch") loadTwitchTop();
    if (view === "youtube") ytView(currentYt);
  }

  function loadHistory() {
    api("GET", "history").then(function (r) {
      renderCards("#start-history", (r.items || []).map(historyItem), "Angefangene Videos erscheinen hier.");
    }).catch(function () {});
  }

  // --- PurpleTV (Twitch) -----------------------------------------------------------------

  var twitchTop = [];
  function loadTwitchTop() {
    api("GET", "twitch/top").then(function (r) {
      twitchTop = (r.channels || []).map(twitchItem);
      renderCards("#tw-top", twitchTop, "Twitch lädt noch – gleich nochmal versuchen.");
      if (state) render(state);
    }).catch(function () {});
  }
  $("#tw-search").addEventListener("submit", function (e) {
    e.preventDefault();
    var q = $("#tw-query").value.trim();
    if (!q) return;
    $("#tw-query").blur();
    $("#tw-results-shelf").hidden = false;
    $("#tw-results").innerHTML = '<p class="empty-note">Suche…</p>';
    lastKeys["tw-results"] = null;
    api("GET", "twitch/search", { q: q }).then(function (r) {
      renderCards("#tw-results", (r.channels || []).map(twitchItem), "Nichts gefunden.");
    }).catch(function (err) { $("#tw-results").innerHTML = ""; $("#tw-results").appendChild(el("p", { "class": "empty-note", text: err.message })); });
  });

  // --- YouTube ---------------------------------------------------------------------------

  var currentYt = null;
  var ytTrends = [];
  function loadYtTrendsForStart() {
    api("GET", "youtube/shelves").then(function (r) {
      var shelves = (r.shelves || []).filter(function (sh) { return sh.videos && sh.videos.length; });
      // Prefer a real trend list over random live streams.
      shelves.sort(function (a, b) { return (a.id === "live") - (b.id === "live"); });
      ytTrends = shelves.length ? shelves[0].videos.map(ytItem) : [];
      if (state) render(state);
    }).catch(function () {});
  }
  function ytView(view) {
    // Without subscriptions the feed is empty: open the trends first.
    if (!view) view = state && (state.youtube_feed || []).length ? "feed" : "trends";
    currentYt = view;
    $$("[data-yt]").forEach(function (c) { c.classList.toggle("active", c.getAttribute("data-yt") === view); });
    $$("[data-yt-view]").forEach(function (v) { v.hidden = v.getAttribute("data-yt-view") !== view; });
    if (view === "feed") loadFeed();
    if (view === "trends") loadTrends();
    if (view === "manage") loadSubs();
  }
  $$("[data-yt]").forEach(function (c) { c.addEventListener("click", function () { ytView(c.getAttribute("data-yt")); }); });

  function loadFeed() {
    api("GET", "youtube/feed").then(function (r) {
      renderCards("#yt-feed", (r.videos || []).map(ytItem), "Noch keine Abos. Unter „Kanäle verwalten“ oder über die Suche hinzufügen.");
    }).catch(function () {});
  }
  function loadTrends() {
    api("GET", "youtube/shelves").then(function (r) {
      var box = $("#yt-trends");
      box.innerHTML = "";
      (r.shelves || []).forEach(function (sh) {
        if (!sh.videos || !sh.videos.length) return;
        var cards = el("div", { "class": "cards", id: "yt-shelf-" + sh.id });
        box.appendChild(el("div", { "class": "shelf" }, [el("h3", { text: sh.title }), cards]));
        renderCards(cards, sh.videos.map(ytItem));
      });
      if (!box.childElementCount) box.appendChild(el("p", { "class": "empty-note", text: "Trends werden auf dem TV noch geladen." }));
    }).catch(function () {});
  }

  $("#yt-search").addEventListener("submit", function (e) {
    e.preventDefault();
    var q = $("#yt-query").value.trim();
    if (!q) return;
    $("#yt-query").blur();
    // A pasted link plays right away.
    if (/youtu\.?be/.test(q)) {
      post("open", { url: q }).then(function () { toast("▶ Läuft auf dem TV"); }).catch(function () {});
      return;
    }
    ytView("results");
    $("#yt-videos").innerHTML = '<p class="empty-note">Suche…</p>';
    lastKeys["yt-videos"] = null;
    $("#yt-channels").innerHTML = "";
    api("GET", "youtube/search", { q: q }).then(function (r) {
      (r.channels || []).forEach(function (c) {
        var btn = el("button", { "class": "sub-btn" + (c.subscribed ? " done" : ""), text: c.subscribed ? "Abonniert" : "Abonnieren" });
        btn.addEventListener("click", function () {
          if (btn.classList.contains("done")) return;
          btn.textContent = "…";
          post("youtube/subscribe", { url: c.url }).then(function (res) {
            btn.textContent = "Abonniert";
            btn.classList.add("done");
            toast(((res.channel && res.channel.name) || "Kanal") + " abonniert");
          }).catch(function () { btn.textContent = "Abonnieren"; });
        });
        $("#yt-channels").appendChild(el("div", { "class": "channel" }, [
          el("span", { "class": "avatar", style: bg(c.avatar) }), el("span", { text: c.name }), btn,
        ]));
      });
      renderCards("#yt-videos", (r.videos || []).map(ytItem), "Nichts gefunden.");
    }).catch(function (err) { $("#yt-videos").innerHTML = ""; $("#yt-videos").appendChild(el("p", { "class": "empty-note", text: err.message })); });
  });
  $("#yt-show-tv").addEventListener("click", function () {
    var q = $("#yt-query").value.trim();
    if (q) post("youtube/search", { q: q }).then(function () { toast("Suche auf dem TV geöffnet"); }).catch(function () {});
  });

  function loadSubs() {
    api("GET", "youtube/subscriptions").then(function (r) {
      var list = r.channels || [], box = $("#yt-subs");
      $("#yt-sub-count").textContent = list.length ? String(list.length) : "";
      box.innerHTML = "";
      if (!list.length) box.appendChild(el("p", { "class": "empty-note", text: "Noch keine Abos." }));
      list.forEach(function (c) {
        box.appendChild(el("div", { "class": "item" }, [
          el("span", { "class": "avatar", style: bg(c.avatar) }),
          el("span", { "class": "text", text: c.name }),
          el("button", { "class": "btn ghost small", text: "Entfernen", onclick: function () { post("youtube/unsubscribe", { id: c.id }).then(loadSubs).catch(function () {}); } }),
        ]));
      });
    }).catch(function () {});
  }
  $("#yt-add-btn").addEventListener("click", function () {
    var lines = $("#yt-add").value.split(/\n|,/).map(function (s) { return s.trim(); }).filter(Boolean);
    if (!lines.length) return;
    var status = $("#yt-add-status"), done = 0, failed = [];
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

  // --- Share -----------------------------------------------------------------------------

  function httpOrigin() { return "http://" + location.hostname + ":" + HTTP_PORT; }
  function secureUrl() { return "https://" + location.hostname + ":" + ((state && state.https_port) || 8766) + "/?code=" + encodeURIComponent(code) + "#share"; }

  function setupShare() {
    var origin = DEMO ? "http://192.168.1.20:8765" : httpOrigin();
    $("#bookmarklet").href = "javascript:(function(){window.open('" + origin + "/send?code=" + encodeURIComponent(code) +
      "&url='+encodeURIComponent(location.href),'couch','width=440,height=420')})()";
    $("#bookmarklet").addEventListener("click", function (e) { e.preventDefault(); toast("In die Lesezeichenleiste ziehen"); });
    $("#shortcut-url").textContent = origin + "/api/open?token=" + code + "&url=";
    $("#pair-url").textContent = origin + "/?code=" + code;
    $("#log-link").href = "/api/log?token=" + encodeURIComponent(code);
    $("#secure-link").href = DEMO ? "#" : secureUrl();
    var canScreen = !!(navigator.mediaDevices && navigator.mediaDevices.getDisplayMedia) || !window.isSecureContext && !/iPhone|iPad|Android/.test(navigator.userAgent);
    $("#screen-start").hidden = !canScreen;
    $("#ios-note").hidden = canScreen;
    if (!canScreen) $("#screen-text").textContent = "Zeig deine Kamera live auf dem TV – oder teil Fotos und Videos oben.";
  }
  $$("[data-copy]").forEach(function (b) { b.addEventListener("click", function () { copy($("#" + b.getAttribute("data-copy")).textContent); }); });

  $("#link-form").addEventListener("submit", function (e) {
    e.preventDefault();
    var v = $("#link-input").value.trim();
    if (!v) return;
    post("open", /^https?:|^couch:|^purpletv:/.test(v) ? { url: v } : { login: v }).then(function () {
      toast("▶ Läuft auf dem TV");
      $("#link-input").value = "";
    }).catch(function () {});
  });

  // Photos and videos
  $("#media-input").addEventListener("change", function () {
    var files = Array.prototype.slice.call(this.files || []);
    this.value = "";
    if (!files.length) return;
    var i = 0, status = $("#media-status"), bar = $("#media-bar");
    $("#media-progress").hidden = false;
    (function next() {
      if (i >= files.length) {
        status.textContent = files.length === 1 ? "Wird auf dem TV gezeigt." : files.length + " Dateien auf dem TV – ◀ ▶ blättert.";
        setTimeout(function () { $("#media-progress").hidden = true; }, 1500);
        return;
      }
      var f = files[i++];
      status.textContent = "Sende " + f.name + (files.length > 1 ? " (" + i + "/" + files.length + ")" : "") + "…";
      if (DEMO) { bar.style.width = "100%"; return setTimeout(next, 400); }
      var form = new FormData();
      form.append("file", f, f.name);
      var xhr = new XMLHttpRequest();
      xhr.open("POST", "/api/media?token=" + encodeURIComponent(code) + "&name=" + encodeURIComponent(f.name));
      xhr.upload.onprogress = function (ev) { if (ev.lengthComputable) bar.style.width = Math.round(ev.loaded / ev.total * 100) + "%"; };
      xhr.onload = function () {
        if (xhr.status >= 300) { status.textContent = f.name + ": " + ((safeJson(xhr.responseText) || {}).error || "Fehler " + xhr.status); return; }
        next();
      };
      xhr.onerror = function () { status.textContent = "TV nicht erreichbar."; };
      xhr.send(form);
    })();
  });
  function safeJson(t) { try { return JSON.parse(t); } catch (e) { return null; } }

  // Screen / camera sharing over WebRTC, directly to the TV.
  var share = { pc: null, stream: null, id: null, timer: null };
  function gathered(pc) {
    return new Promise(function (resolve) {
      if (pc.iceGatheringState === "complete") return resolve();
      pc.addEventListener("icegatheringstatechange", function () { if (pc.iceGatheringState === "complete") resolve(); });
      setTimeout(resolve, 2500);
    });
  }
  function startShare(kind) {
    if (DEMO) return toast("Im Demo-Modus nicht verfügbar");
    if (!window.isSecureContext || !navigator.mediaDevices) {
      $("#secure-notice").hidden = false;
      $("#secure-notice").scrollIntoView({ behavior: "smooth", block: "center" });
      return;
    }
    var media = kind === "camera"
      ? navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment", width: { ideal: 1920 }, height: { ideal: 1080 } }, audio: true })
      : navigator.mediaDevices.getDisplayMedia({ video: { frameRate: { ideal: 30 }, width: { ideal: 1920 }, height: { ideal: 1080 } }, audio: true });
    media.then(function (stream) {
      share.stream = stream;
      var preview = $("#screen-preview");
      preview.srcObject = stream;
      preview.hidden = false;
      preview.play().catch(function () {});
      stream.getVideoTracks()[0].addEventListener("ended", stopShare);
      var pc = share.pc = new RTCPeerConnection({ iceServers: [] });
      stream.getTracks().forEach(function (t) { pc.addTrack(t, stream); });
      setSharing(true, "Verbinde mit dem TV…");
      return pc.createOffer()
        .then(function (o) { return pc.setLocalDescription(o); })
        .then(function () { return gathered(pc); })
        .then(function () { return post("screen/offer", null, { sdp: pc.localDescription.sdp, kind: kind, from: deviceName() }); })
        .then(function (r) { share.id = r.id; return waitAnswer(); });
    }).catch(function (e) {
      if (e && e.name === "NotAllowedError") toast("Freigabe abgebrochen");
      else if (e && e.message !== "auth") toast("Teilen nicht möglich: " + (e.message || e));
      stopShare();
    });
  }
  function waitAnswer() {
    var tries = 0;
    return new Promise(function (resolve, reject) {
      share.timer = setInterval(function () {
        if (++tries > 60) { clearInterval(share.timer); reject(new Error("TV antwortet nicht")); return; }
        api("GET", "screen/answer", { id: share.id }).then(function (r) {
          if (r.ended) { clearInterval(share.timer); stopShare(); return; }
          if (!r.answer || !share.pc || share.pc.remoteDescription) return;
          share.pc.setRemoteDescription({ type: "answer", sdp: r.answer }).then(function () {
            boostBitrate(share.pc);
            setSharing(true, "Läuft auf dem TV");
            resolve();
          });
        }).catch(function () {});
      }, 700);
    });
  }
  function boostBitrate(pc) {
    pc.getSenders().forEach(function (s) {
      if (!s.track || s.track.kind !== "video") return;
      var p = s.getParameters();
      if (!p.encodings || !p.encodings.length) p.encodings = [{}];
      p.encodings[0].maxBitrate = 8000000;
      s.setParameters(p).catch(function () {});
    });
  }
  function stopShare() {
    clearInterval(share.timer);
    if (share.id) send("screen/stop", { id: share.id });
    if (share.pc) share.pc.close();
    if (share.stream) share.stream.getTracks().forEach(function (t) { t.stop(); });
    share = { pc: null, stream: null, id: null, timer: null };
    $("#screen-preview").hidden = true;
    setSharing(false);
  }
  function setSharing(on, text) {
    $("#screen-stop").hidden = !on;
    $("#screen-start").disabled = on;
    $("#camera-start").disabled = on;
    if (text) toast(text);
  }
  $("#screen-start").addEventListener("click", function () { startShare("screen"); });
  $("#camera-start").addEventListener("click", function () { startShare("camera"); });
  $("#screen-stop").addEventListener("click", stopShare);
  window.addEventListener("pagehide", function () { if (share.id) stopShare(); });

  // --- Greeting --------------------------------------------------------------------------

  (function () {
    var h = new Date().getHours();
    $("#greeting").textContent = h < 11 && h >= 5 ? "Guten Morgen" : h < 18 ? "Hallo" : h < 23 ? "Guten Abend" : "Gute Nacht";
  })();

  // --- Demo data (preview without a TV) --------------------------------------------------

  function demoApi(method, path) {
    var now = Date.now();
    // Generated placeholder pictures, so the demo works offline.
    var thumb = function (seed) {
      var h = 0;
      for (var i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) % 360;
      var svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 360"><defs><linearGradient id="g" x1="0" y1="1" x2="1" y2="0">' +
        '<stop offset="0" stop-color="hsl(' + h + ',55%,22%)"/><stop offset="1" stop-color="hsl(' + ((h + 50) % 360) + ',70%,48%)"/></linearGradient></defs>' +
        '<rect width="640" height="360" fill="url(#g)"/><circle cx="470" cy="120" r="140" fill="rgba(255,255,255,.08)"/></svg>';
      return "data:image/svg+xml," + encodeURIComponent(svg);
    };
    var DATA = {
      state: {
        version: "2.0.0", screen: "live", app: "twitch", apps: ["twitch", "youtube"], playing: true, paused: false, muted: false, volume: 0.6, https_port: 8766,
        media: { app: "twitch", kind: "live", id: "papaplatte", channel_id: "papaplatte", title: "CRAFTATTACK TAG 9 | !merch", channel: "Papaplatte", game: "Minecraft", viewers: 36214, thumbnail: thumb("craft"), live: true },
        followed_live: [
          { login: "papaplatte", display_name: "Papaplatte", title: "CRAFTATTACK TAG 9 | !merch", game: "Minecraft", viewers: 36214, thumbnail: thumb("craft"), avatar: thumb("pp") },
          { login: "zarbex", display_name: "zarbex", title: "Chillen und quatschen", game: "Just Chatting", viewers: 4200, thumbnail: thumb("chat"), avatar: thumb("zx") },
          { login: "eliasn97", display_name: "Eliasn97", title: "FIFA Weekend League", game: "EA Sports FC", viewers: 12800, thumbnail: thumb("fifa"), avatar: thumb("el") },
        ],
        youtube_feed: [
          { id: "a1", title: "Roomtour 2026 – das neue Studio", channel: "Kanal A", thumbnail: thumb("room"), duration: 912, uploaded_ms: now - 2 * 3600e3 },
          { id: "a2", title: "Ich habe 30 Tage nur gekocht", channel: "Kanal B", thumbnail: thumb("cook"), duration: 1405, uploaded_ms: now - 26 * 3600e3 },
          { id: "a3", title: "Live: Nachrichten am Abend", channel: "News", thumbnail: thumb("news"), live: true },
          { id: "a4", title: "Der beste Laptop für Studenten", channel: "Tech", thumbnail: thumb("tech"), duration: 734, uploaded_ms: now - 3 * 86400e3 },
        ],
      },
      history: { items: [
        { app: "youtube", id: "a2", title: "Ich habe 30 Tage nur gekocht", channel: "Kanal B", thumbnail: thumb("cook"), progress: 0.42, duration: 1405, position: 590 },
        { app: "twitch", id: "123", title: "Stream vom Freitag", channel: "Papaplatte", thumbnail: thumb("vod"), progress: 0.7, duration: 14400 },
      ] },
    };
    DATA["twitch/top"] = { channels: DATA.state.followed_live.slice().reverse() };
    DATA["twitch/search"] = { channels: DATA.state.followed_live.slice(0, 1) };
    DATA["youtube/feed"] = { videos: DATA.state.youtube_feed };
    DATA["youtube/shelves"] = { shelves: [{ id: "gaming", title: "Gaming", videos: DATA.state.youtube_feed.slice().reverse() }] };
    DATA["youtube/search"] = { videos: DATA.state.youtube_feed, channels: [{ id: "UCa", url: "#", name: "Kanal A", avatar: thumb("ka") }] };
    DATA["youtube/subscriptions"] = { channels: [{ id: "UCa", name: "Kanal A", avatar: thumb("ka") }, { id: "UCb", name: "Kanal B", avatar: thumb("kb") }] };
    if (method === "POST") {
      toast("Demo: würde jetzt auf dem TV ausgeführt");
      return Promise.resolve({ ok: true });
    }
    return Promise.resolve(DATA[path] || {});
  }

  // --- Boot ------------------------------------------------------------------------------

  if (!code) {
    showPairing();
  } else if (location.pathname === "/send") {
    sendFlow();
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
