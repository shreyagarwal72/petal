// content.js — Petal Media Grabber Content Script
const api = typeof browser !== "undefined" ? browser : chrome;
var chrome = api;

// Inject our page context hook script to detect media URLs and manage native player handoff
const script = document.createElement('script');
script.src = chrome.runtime.getURL('inject.js');
script.onload = function() { this.remove(); };
(document.head || document.documentElement).appendChild(script);

// On start, request the current native player state from the background script
try {
    chrome.runtime.sendMessage({ type: 'GET_NATIVE_PLAYER_STATE' })
        .then((response) => {
            if (response && response.hasOwnProperty('enabled')) {
                window.postMessage({
                    type: 'OMNI_SET_NATIVE_PLAYER',
                    enabled: response.enabled,
                    youtubeEnabled: response.youtubeEnabled
                }, '*');
            }
        })
        .catch(() => {});
} catch (e) {
    console.error('[content.js] Failed to request native player state:', e);
}

// Request previously cached media URLs for this tab on startup & periodically
function refreshTabMedia() {
    try {
        chrome.runtime.sendMessage({ type: 'GET_TAB_MEDIA' })
            .then((response) => {
                if (response && Array.isArray(response)) {
                    response.forEach(item => {
                        window.postMessage({
                            type: 'ADD_DETECTED_MANIFEST',
                            url: item.url,
                            mimeType: item.mimeType
                        }, '*');
                    });
                }
            })
            .catch(() => {});
    } catch (e) {}
}
refreshTabMedia();
setTimeout(refreshTabMedia, 1200);
setTimeout(refreshTabMedia, 3000);
setTimeout(refreshTabMedia, 6000);


// Reader Mode article eligibility detector
function checkArticleEligibility() {
    try {
        const articleEl = document.querySelector('article, [itemprop="articleBody"], .entry-content, .post-content, main, .article-content, #article-body');
        const paragraphs = document.querySelectorAll('p');
        let textLength = 0;
        for (let i = 0; i < paragraphs.length; i++) {
            textLength += (paragraphs[i].innerText || '').length;
        }
        const isArticle = !!(articleEl || (paragraphs.length >= 3 && textLength > 600));
        if (isArticle) {
            chrome.runtime.sendMessage({
                type: 'READER_ELIGIBLE_DETECTED',
                pageUrl: window.location.href,
                isArticle: true
            }).catch(() => {});
        }
    } catch (e) {}
}

if (document.readyState === 'complete' || document.readyState === 'interactive') {
    checkArticleEligibility();
} else {
    document.addEventListener('DOMContentLoaded', checkArticleEligibility);
}
window.addEventListener('load', checkArticleEligibility);
setTimeout(checkArticleEligibility, 1500);
setTimeout(checkArticleEligibility, 3500);

// DOM resource discovery: videos, audios, high-res images, and documents/media links
function scanDomResources() {
    try {
        checkArticleEligibility();
        const results = [];
        const seenUrls = new Set();

        // 1. Videos, Audio & Source tags
        document.querySelectorAll('video, audio, source').forEach(el => {
            const src = el.src || el.currentSrc || el.getAttribute('data-src');
            if (src && src.startsWith('http') && !seenUrls.has(src)) {
                seenUrls.add(src);
                const tag = (el.tagName || '').toLowerCase();
                const type = tag === 'audio' ? 'audio' : 'video';
                results.push({ url: src, type: type });
            }
        });

        // 2. High-res images
        document.querySelectorAll('img').forEach(img => {
            const src = img.currentSrc || img.src || img.getAttribute('data-src');
            if (src && src.startsWith('http') && (img.naturalWidth > 100 || img.width > 100) && !seenUrls.has(src)) {
                seenUrls.add(src);
                results.push({ url: src, type: 'image' });
            }
        });

        // 3. Document and media links
        document.querySelectorAll('a[href]').forEach(a => {
            const href = a.href;
            if (href && /\.(pdf|zip|rar|7z|apk|tar|gz|epub|m3u8|mp4|mp3)(\?|$)/i.test(href) && !seenUrls.has(href)) {
                seenUrls.add(href);
                let type = 'doc';
                if (/\.(m3u8|mp4)(\?|$)/i.test(href)) {
                    type = 'video';
                } else if (/\.mp3(\?|$)/i.test(href)) {
                    type = 'audio';
                }
                const title = (a.innerText || a.title || '').trim();
                results.push({ url: href, type: type, title: title });
            }
        });

        if (results.length > 0) {
            chrome.runtime.sendMessage({
                type: 'DOM_RESOURCES_SNIFFED',
                items: JSON.stringify(results),
                pageUrl: window.location.href
            }).catch(() => {});
        }
    } catch (e) {}
}

if (document.readyState === 'complete') {
    scanDomResources();
} else {
    document.addEventListener('DOMContentLoaded', scanDomResources);
}
window.addEventListener('load', scanDomResources);
setTimeout(scanDomResources, 2000);

// Listen for messages from background script (network-detected manifests + native player config + handoff control)
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (!message || !message.type) return;

    if (message.type === 'NETWORK_MEDIA_DETECTED') {
        window.postMessage({
            type: 'ADD_DETECTED_MANIFEST',
            url: message.url,
            mimeType: message.mimeType
        }, '*');
    } else if (message.type === 'OMNI_SET_NATIVE_PLAYER') {
        window.postMessage({
            type: 'OMNI_SET_NATIVE_PLAYER',
            enabled: message.enabled,
            youtubeEnabled: message.youtubeEnabled
        }, '*');
    } else if (message.type === 'PAUSE_AND_LAUNCH') {
        window.postMessage({
            type: 'PAUSE_AND_LAUNCH',
            handoffId: message.handoffId,
            sessionId: message.sessionId,
            videoId: message.videoId
        }, '*');
    } else if (message.type === 'RESUME_WEBSITE') {
        window.postMessage({
            type: 'RESUME_WEBSITE',
            handoffId: message.handoffId,
            sessionId: message.sessionId,
            videoId: message.videoId
        }, '*');
    } else if (message.type === 'RESTORE_VIDEO_STATE') {
        window.postMessage({
            type: 'RESTORE_VIDEO_STATE',
            payload: message.payload || message
        }, '*');
    } else if (message.type === 'HANDOFF_COMPLETE') {
        window.postMessage({
            type: 'HANDOFF_COMPLETE',
            sessionId: message.sessionId
        }, '*');
    } else if (message.type === 'EVAL_JS') {
        window.postMessage({
            type: 'EVAL_JS',
            script: message.script
        }, '*');
    }
});

function extractArticleData() {
    try {
        var el = document.querySelector('article, [itemprop="articleBody"], .entry-content, .post-content, main, .article-body, #content') || document.body;
        var t = (document.querySelector('h1') ? document.querySelector('h1').innerText : document.title) || '';
        var by = (document.querySelector('[rel="author"], .author, .byline, [itemprop="author"]') ? document.querySelector('[rel="author"], .author, .byline, [itemprop="author"]').innerText : '') || '';
        var img = document.querySelector('article img, [itemprop="image"], meta[property="og:image"]');
        var lead = img ? (img.src || img.content || '') : '';
        var bodyText = el ? (el.innerText || '') : '';
        return { title: t, author: by, lead: lead, text: bodyText };
    } catch(err) {
        return { title: document.title || '', author: '', lead: '', text: document.body ? document.body.innerText : '' };
    }
}

// Relay messages from the page-level inject.js hook to background.js
window.addEventListener('message', (event) => {
    if (event.source !== window) return;
    const data = event.data;
    if (!data || !data.type) return;

    if (data.type === 'PETAL_EXTRACT_ARTICLE') {
        var articlePayload = data.article || extractArticleData();
        chrome.runtime.sendMessage({
            type: 'READER_ARTICLE_DATA',
            article: articlePayload,
            pageUrl: window.location.href
        }).catch(() => {});
    } else if (data.type === 'REQUEST_HANDOFF') {
        chrome.runtime.sendMessage({
            type: 'REQUEST_HANDOFF',
            url: data.url,
            pageUrl: data.pageUrl,
            associatedStreams: data.associatedStreams || [],
            handoff: data.handoff,
            mimeType: data.mimeType
        });
    } else if (data.type === 'REQUEST_DOWNLOAD') {
        chrome.runtime.sendMessage({
            type: 'REQUEST_DOWNLOAD',
            url: data.url,
            pageUrl: data.pageUrl,
            associatedStreams: data.associatedStreams || [],
            mimeType: data.mimeType,
            title: data.title,
            videoId: data.videoId,
            requestId: data.requestId
        });
    } else if (data.type === 'SITE_DOWNLOAD_REQUEST') {
        chrome.runtime.sendMessage({
            type: 'SITE_DOWNLOAD_REQUEST',
            url: data.url,
            pageUrl: data.pageUrl,
            mimeType: data.mimeType,
            title: data.title,
            cookies: data.cookies || ''
        });
    } else if (data.type === 'HANDOFF_RESTORED') {
        chrome.runtime.sendMessage({
            type: 'HANDOFF_RESTORED',
            sessionId: data.sessionId,
            videoId: data.videoId,
            currentTimeMs: data.currentTimeMs,
            isPlaying: data.isPlaying
        });
    } else if (data.type === 'MSE_MEDIA_STREAM_GRABBED') {
        chrome.runtime.sendMessage({
            type: 'MEDIA_GRABBED',
            url: data.url,
            mimeType: data.mimeType
        });
    } else if (data.type === 'VIDEO_STATE_CHANGE') {
        chrome.runtime.sendMessage({
            type: 'VIDEO_STATE_CHANGE',
            isPlaying: data.isPlaying
        });
    } else if (data.type === 'OMNI_CONSOLE_LOG') {
        chrome.runtime.sendMessage({
            type: 'CONSOLE_LOG',
            level: data.level,
            message: data.message
        });
    } else if (data.type === 'PLAY_IN_NATIVE') {
        chrome.runtime.sendMessage({
            type: 'PLAY_IN_NATIVE',
            url: data.url,
            pageUrl: data.pageUrl,
            mimeType: data.mimeType
        });
    } else if (data.type === 'OMNI_FOCUS_LOGIN_INPUT') {
        chrome.runtime.sendMessage({
            type: 'FOCUS_LOGIN_INPUT'
        });
    } else if (data.type === 'OMNI_INNER_SCROLL_STATE') {
        chrome.runtime.sendMessage({
            type: 'INNER_SCROLL_STATE',
            isScrolled: data.isScrolled
        });
    } else if (data.type === 'PETAL_DOM_MEDIA_SNIFFED') {
        if (data.payload && Array.isArray(data.payload.items)) {
            chrome.runtime.sendMessage({
                type: 'DOM_RESOURCES_SNIFFED',
                items: JSON.stringify(data.payload.items),
                pageUrl: data.payload.pageUrl || window.location.href
            }).catch(() => {});
        }
    }
});
