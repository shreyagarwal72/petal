/* Petal Arc-Style Boosts Web Extension */
(function() {
  'use strict';

  const host = window.location.hostname;
  let customStyleTag = null;

  function getOrCreateStyleTag() {
    if (!customStyleTag || !document.head.contains(customStyleTag)) {
      customStyleTag = document.createElement('style');
      customStyleTag.id = 'petal-boost-styles';
      (document.head || document.documentElement).appendChild(customStyleTag);
    }
    return customStyleTag;
  }

  // Load saved boost rules for this host from localStorage
  function applySavedBoost() {
    try {
      const data = localStorage.getItem('petal_boost_' + host);
      if (!data) return;
      const boost = JSON.parse(data);
      let css = '';

      if (boost.theme === 'oled') {
        css += `
          html, body { background-color: #000000 !important; color: #e6e6e6 !important; }
          div, section, article, main, header, nav, footer { background-color: transparent !important; }
          a { color: #80bfff !important; }
        `;
      } else if (boost.theme === 'warm_paper') {
        css += `
          html, body { background-color: #f7f3e8 !important; color: #2e261f !important; }
          a { color: #8f4b1e !important; }
        `;
      } else if (boost.theme === 'cyberpunk') {
        css += `
          html, body { background-color: #0d0e15 !important; color: #00ffcc !important; }
          a { color: #ff007f !important; }
        `;
      }

      if (boost.fontFamily) {
        css += `* { font-family: ${boost.fontFamily} !important; }`;
      }

      if (boost.zappedSelectors && boost.zappedSelectors.length > 0) {
        css += `${boost.zappedSelectors.join(', ')} { display: none !important; }`;
      }

      getOrCreateStyleTag().textContent = css;
    } catch (e) {
      console.error('[PetalBoosts] failed to load boost:', e);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', applySavedBoost);
  } else {
    applySavedBoost();
  }

  // Live boost message handler from Petal Native bridge
  window.__petalApplyBoost = function(boostJson) {
    try {
      const boost = typeof boostJson === 'string' ? JSON.parse(boostJson) : boostJson;
      localStorage.setItem('petal_boost_' + host, JSON.stringify(boost));
      applySavedBoost();
      return true;
    } catch(e) {
      return false;
    }
  };

  // Interactive Element Zapper mode
  let zapperActive = false;
  let hoveredEl = null;

  function onZapperMouseOver(e) {
    if (!zapperActive) return;
    if (hoveredEl && hoveredEl !== e.target) {
      hoveredEl.style.outline = '';
    }
    hoveredEl = e.target;
    hoveredEl.style.outline = '3px dashed #ff3366';
  }

  function onZapperClick(e) {
    if (!zapperActive) return;
    e.preventDefault();
    e.stopPropagation();
    if (hoveredEl) {
      hoveredEl.style.outline = '';
      hoveredEl.style.display = 'none';
      // Record selector if possible
      let sel = hoveredEl.id ? '#' + hoveredEl.id : (hoveredEl.className ? '.' + hoveredEl.className.trim().split(/\s+/).join('.') : hoveredEl.tagName.toLowerCase());
      try {
        const key = 'petal_boost_' + host;
        const current = JSON.parse(localStorage.getItem(key) || '{}');
        current.zappedSelectors = current.zappedSelectors || [];
        if (!current.zappedSelectors.includes(sel)) {
          current.zappedSelectors.push(sel);
          localStorage.setItem(key, JSON.stringify(current));
        }
      } catch(_) {}
    }
  }

  window.__petalToggleElementZapper = function(active) {
    zapperActive = active;
    if (active) {
      document.addEventListener('mouseover', onZapperMouseOver, true);
      document.addEventListener('click', onZapperClick, true);
      document.body.style.cursor = 'crosshair';
    } else {
      document.removeEventListener('mouseover', onZapperMouseOver, true);
      document.removeEventListener('click', onZapperClick, true);
      document.body.style.cursor = '';
      if (hoveredEl) hoveredEl.style.outline = '';
    }
    return zapperActive;
  };
})();
