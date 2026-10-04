// background.js for Google Search Fixer
// Based on official Mozilla Firefox google-search-fixer (Thomas Wisniewski)
// Avoids injecting fake Chromium Client Hints (Sec-CH-UA) from Gecko engine
// which causes Google's anti-bot system to flag requests and require CAPTCHAs.

const CHROME_MOBILE_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36";

browser.webRequest.onBeforeSendHeaders.addListener(
    function(details) {
        // If query originates from official Firefox client search, preserve genuine Firefox headers
        if (details.url && (details.url.includes("client=firefox") || details.url.includes("client=firefox-b-m"))) {
            return;
        }

        let headers = details.requestHeaders || [];
        // Strip any spoofed or inconsistent Sec-CH-UA client hints that trigger bot detection
        headers = headers.filter(h => !h.name.toLowerCase().startsWith("sec-ch-ua"));

        let hasUa = false;
        for (let i = 0; i < headers.length; i++) {
            if (headers[i].name.toLowerCase() === "user-agent") {
                headers[i].value = CHROME_MOBILE_UA;
                hasUa = true;
                break;
            }
        }
        if (!hasUa) {
            headers.push({ name: "User-Agent", value: CHROME_MOBILE_UA });
        }

        return { requestHeaders: headers };
    },
    {
        urls: [
            "*://*.google.com/search*",
            "*://*.google.com/webhp*",
            "*://*.google.com/m*",
            "*://*.google.co.*/search*",
            "*://*.google.co.*/webhp*",
            "*://*.google.co.*/m*",
            "*://*.google.*/search*",
            "*://*.google.*/webhp*",
            "*://*.google.*/m*"
        ],
        types: ["main_frame", "sub_frame"]
    },
    ["blocking", "requestHeaders"]
);

// Block Google service worker which can conflict with GeckoView session caching
browser.webRequest.onBeforeRequest.addListener(
    function(details) {
        return { cancel: true };
    },
    {
        urls: [
            "*://*.google.com/serviceworker*",
            "*://*.google.co.*/serviceworker*",
            "*://*.google.*/serviceworker*"
        ],
        types: ["script"]
    },
    ["blocking"]
);
