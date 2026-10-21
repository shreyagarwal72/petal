/* Petal Bionic Reading Script */
(function() {
  'use strict';

  let isBionicActive = false;
  const originalNodes = new Map();

  function bionicWord(word) {
    if (!word || word.length <= 1) return word;
    let mid;
    if (word.length <= 3) mid = 1;
    else if (word.length <= 5) mid = 2;
    else if (word.length <= 8) mid = 3;
    else mid = Math.ceil(word.length * 0.45);

    const boldPart = word.substring(0, mid);
    const restPart = word.substring(mid);
    return '<b>' + boldPart + '</b>' + restPart;
  }

  function bionicText(text) {
    const tokens = text.split(/(\s+|[^\p{L}\p{N}]+)/u);
    return tokens.map(token => {
      if (/^[\p{L}\p{N}]+$/u.test(token)) {
        return bionicWord(token);
      }
      return token;
    }).join('');
  }

  function processNode(node) {
    if (node.nodeType === Node.TEXT_NODE) {
      const val = node.nodeValue;
      if (!val || val.trim().length === 0) return;
      if (node.parentElement && ['SCRIPT', 'STYLE', 'TEXTAREA', 'INPUT', 'CODE', 'PRE'].includes(node.parentElement.tagName)) return;

      const span = document.createElement('span');
      span.className = 'petal-bionic-text';
      span.innerHTML = bionicText(val);
      originalNodes.set(span, val);
      node.parentNode.replaceChild(span, node);
    } else if (node.nodeType === Node.ELEMENT_NODE) {
      if (['SCRIPT', 'STYLE', 'TEXTAREA', 'INPUT', 'CODE', 'PRE'].includes(node.tagName)) return;
      Array.from(node.childNodes).forEach(processNode);
    }
  }

  window.__petalToggleBionicReading = function(enable) {
    if (enable === undefined) enable = !isBionicActive;
    if (enable === isBionicActive) return isBionicActive;

    if (enable) {
      const targets = document.querySelectorAll('p, li, article, section, h1, h2, h3, h4, h5, h6, blockquote, dd, dt');
      targets.forEach(processNode);
      isBionicActive = true;
    } else {
      document.querySelectorAll('.petal-bionic-text').forEach(span => {
        const orig = originalNodes.get(span) || span.textContent;
        const textNode = document.createTextNode(orig);
        span.parentNode.replaceChild(textNode, span);
      });
      originalNodes.clear();
      isBionicActive = false;
    }
    return isBionicActive;
  };
})();
