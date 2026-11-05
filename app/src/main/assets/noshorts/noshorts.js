// Longform: Shorts removal, second line. Runs at document_start on every
// youtube.com page (see manifest.json); noshorts.css does the instant hiding.
(() => {
  'use strict';

  const HIDDEN = 'data-longform-hidden';
  const SHORTS_LABEL = /^\s*shorts\s*$/i;
  const REMIX_LABEL = /^\s*remix\s*$/i;

  // Elements identified by their visible label ("Shorts" is not translated in any YouTube language).
  const LABELLED = [
    'ytm-pivot-bar-item-renderer',   // bottom navigation
    'yt-tab-shape',                  // channel tabs
    'ytm-chip-cloud-chip-renderer',  // filter chips
    'yt-chip-cloud-chip-renderer',
    'chip-view-model',
    'ytm-sub-menu-item-renderer',
  ].join(',');
  const SHELVES = 'ytm-shelf-renderer, ytm-rich-section-renderer, grid-shelf-view-model, ytm-reel-shelf-renderer';
  const SHELF_TITLE = 'yt-shelf-header-layout, yt-section-header-view-model, .reel-shelf-title';
  // Parents whose direct children are single feed entries.
  const LIST = 'lazy-list, .rich-grid-renderer-contents, ytm-rich-grid-renderer, ytm-section-list-renderer';
  // Links inside text (descriptions, comments) never hide anything.
  const TEXT = '.yt-core-attributed-string, yt-attributed-string, ytm-comment-renderer, ytm-comment-thread-renderer';

  /** Where a YouTube URL should go instead, or null if it is fine. */
  function replacementFor(href) {
    let u;
    try { u = new URL(href, location.href); } catch (e) { return null; }
    if (!/(^|\.)youtube\.com$/i.test(u.hostname)) return null;
    const p = u.pathname;
    let m = p.match(/^\/shorts\/([A-Za-z0-9_-]{6,})/);
    if (m) return u.origin + '/watch?v=' + m[1];                   // a Short -> normal player
    if (/^\/(feed\/)?shorts\/?$/.test(p)) return u.origin + '/';    // the Shorts feed -> Home
    m = p.match(/^(\/(?:@[^/]+|channel\/[^/]+|c\/[^/]+|user\/[^/]+))\/shorts\/?$/);
    if (m) return u.origin + m[1] + '/videos';                     // channel Shorts tab -> Videos
    return null;
  }

  let lastHref = '';
  function checkLocation() {
    if (location.href === lastHref) return;
    lastHref = location.href;
    const r = replacementFor(location.href);
    if (r) location.replace(r);
  }

  // A tap on anything that links to a Short opens the normal player instead.
  window.addEventListener('click', (ev) => {
    const t = ev.target;
    const a = t && t.closest ? t.closest('a[href]') : null;
    if (!a) return;
    const r = replacementFor(a.href);
    if (!r) return;
    ev.preventDefault();
    ev.stopImmediatePropagation();
    location.assign(r);
  }, true);

  function hide(el) {
    if (el && !el.hasAttribute(HIDDEN)) el.setAttribute(HIDDEN, '');
  }

  function sweep() {
    // Tabs, chips and menu entries labelled "Shorts".
    for (const el of document.querySelectorAll(LABELLED)) {
      if (el.hasAttribute(HIDDEN)) continue;
      if (SHORTS_LABEL.test(el.getAttribute('tab-title') || el.textContent)) hide(el);
    }

    // Shelves whose own header says "Shorts".
    for (const shelf of document.querySelectorAll(SHELVES)) {
      if (shelf.hasAttribute(HIDDEN)) continue;
      const title = shelf.querySelector(SHELF_TITLE);
      if (title && title.closest(SHELVES) === shelf && SHORTS_LABEL.test(title.textContent)) hide(shelf);
    }

    // Any feed entry that links to a Short, whatever YouTube calls the element.
    for (const a of document.querySelectorAll('a[href*="/shorts/"]')) {
      if (a.closest('[' + HIDDEN + ']') || a.closest(TEXT)) continue;
      if (!replacementFor(a.href)) continue;
      let item = a;
      while (item.parentElement && !item.parentElement.matches(LIST)) item = item.parentElement;
      if (item.parentElement) hide(item);
    }

    // "Shorts" in the search filter sheet.
    for (const opt of document.querySelectorAll('ytm-search-filter-group-renderer option, ytm-select option')) {
      if (SHORTS_LABEL.test(opt.textContent)) opt.remove();
    }

    // The watch page's "Remix" button only makes Shorts.
    for (const el of document.querySelectorAll('ytm-slim-video-action-bar-renderer button, ytm-slim-video-action-bar-renderer a')) {
      if (REMIX_LABEL.test(el.textContent)) {
        hide(el.closest('button-view-model, ytm-button-renderer, yt-button-view-model, ytm-slim-metadata-button-renderer') || el);
      }
    }
  }

  let queued = false;
  function schedule() {
    if (queued) return;
    queued = true;
    setTimeout(() => {
      queued = false;
      checkLocation();
      sweep();
    }, 50);
  }

  checkLocation();
  new MutationObserver(schedule).observe(document.documentElement || document, { childList: true, subtree: true });
  window.addEventListener('popstate', checkLocation);
  // YouTube navigates with history.pushState, which content scripts cannot hook; poll the URL.
  setInterval(checkLocation, 250);
})();
