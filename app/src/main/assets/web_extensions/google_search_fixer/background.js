// background.js for Google Search Fixer
// Serves modern interactive search widgets while preserving genuine Chrome Client Hints
// to prevent automated bot detection and first-search Captchas.

const CHROME_VERSION = "131";
const CHROME_MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Mobile; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

browser.webRequest.onBeforeSendHeaders.addListener(
    function(details) {
        let headers = details.requestHeaders;
        let hasUa = false;
        let hasSecChUa = false;
        let hasSecChUaMobile = false;
        let hasSecChUaPlatform = false;

        for (let i = 0; i < headers.length; i++) {
            const name = headers[i].name.toLowerCase();
            if (name === "user-agent") {
                headers[i].value = CHROME_MOBILE_UA;
                hasUa = true;
            } else if (name === "sec-ch-ua") {
                headers[i].value = `"Chromium";v="${CHROME_VERSION}", "Not_A Brand";v="24", "Google Chrome";v="${CHROME_VERSION}"`;
                hasSecChUa = true;
            } else if (name === "sec-ch-ua-mobile") {
                headers[i].value = "?1";
                hasSecChUaMobile = true;
            } else if (name === "sec-ch-ua-platform") {
                headers[i].value = '"Android"';
                hasSecChUaPlatform = true;
            }
        }

        if (!hasUa) {
            headers.push({ name: "User-Agent", value: CHROME_MOBILE_UA });
        }
        if (!hasSecChUa) {
            headers.push({ name: "Sec-CH-UA", value: `"Chromium";v="${CHROME_VERSION}", "Not_A Brand";v="24", "Google Chrome";v="${CHROME_VERSION}"` });
        }
        if (!hasSecChUaMobile) {
            headers.push({ name: "Sec-CH-UA-Mobile", value: "?1" });
        }
        if (!hasSecChUaPlatform) {
            headers.push({ name: "Sec-CH-UA-Platform", value: '"Android"' });
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
