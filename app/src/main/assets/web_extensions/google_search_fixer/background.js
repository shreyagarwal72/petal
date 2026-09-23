// background.js for Google Search Fixer
// Spoofs Chrome for Android User-Agent header specifically on Google Search endpoints
// to serve modern, interactive widgets, AMP cards, and rich layout.

const CHROME_MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Mobile; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36";

browser.webRequest.onBeforeSendHeaders.addListener(
    function(details) {
        for (let header of details.requestHeaders) {
            if (header.name.toLowerCase() === "user-agent") {
                header.value = CHROME_MOBILE_UA;
                break;
            }
        }
        return { requestHeaders: details.requestHeaders };
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
