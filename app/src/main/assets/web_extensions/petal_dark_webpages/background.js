// background.js — Petal Dark Webpages
const api = typeof browser !== "undefined" ? browser : chrome;

let cachedSettings = {
    enabled: true,
    whitelist: [],
    contrast: 90
};

function fetchNativeSettings() {
    return new Promise((resolve) => {
        try {
            api.runtime.sendNativeMessage('petalApp', { type: 'GET_DARK_SETTINGS' })
                .then((response) => {
                    if (response && response.type === 'DARK_SETTINGS_RESPONSE') {
                        cachedSettings = {
                            enabled: response.enabled !== false,
                            whitelist: Array.isArray(response.whitelist) ? response.whitelist : [],
                            contrast: typeof response.contrast === 'number' ? response.contrast : 90
                        };
                        resolve(cachedSettings);
                    } else {
                        resolve(cachedSettings);
                    }
                })
                .catch(() => resolve(cachedSettings));
        } catch (e) {
            resolve(cachedSettings);
        }
    });
}

// Initial fetch on start
fetchNativeSettings();

// Listen to messages from content scripts
api.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (!message || !message.type) return;

    if (message.type === 'GET_DARK_SETTINGS') {
        fetchNativeSettings().then((settings) => {
            sendResponse(settings);
        });
        return true; // Keep channel open for async sendResponse
    }

    if (message.type === 'TOGGLE_DARK_WHITELIST') {
        try {
            api.runtime.sendNativeMessage('petalApp', {
                type: 'TOGGLE_DARK_WHITELIST',
                host: message.host
            }).then(() => {
                fetchNativeSettings().then((updated) => {
                    sendResponse(updated);
                });
            }).catch(() => {
                sendResponse(cachedSettings);
            });
            return true;
        } catch (e) {
            sendResponse(cachedSettings);
        }
    }
});
