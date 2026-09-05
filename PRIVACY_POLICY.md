# Privacy Policy for Petal Browser

**Effective Date:** September 5, 2026  
**Last Updated:** September 5, 2026  

Petal Browser ("we", "our", or "the App") is developed with privacy as a fundamental cornerstone. This Privacy Policy explains how Petal Browser handles user information, device data, and privacy rights when you use our mobile application.

---

## 1. Zero Personal Data Collection Philosophy

Petal Browser does **not** collect, store, transmit, sell, or rent your personal data to our own servers or any first-party infrastructure. 

Specifically:
- **Browsing History & Bookmarks:** Stored exclusively on your local device in a private SQLite database.
- **Search Queries:** Handled directly between your device and your chosen search provider (e.g., Google, DuckDuckGo, Brave Search, Bing, Ecosia). We do not intermediate, record, or track your search history.
- **Downloaded Files:** Saved locally to your device's designated download folder or external download manager.
- **Saved Passwords & Credentials:** Managed via Android Credential Manager or stored securely in your private device sandbox.

---

## 2. Permissions & How They Are Used

Petal Browser requests only the Android permissions necessary to provide core browsing and user-requested features:

- **Internet Access (`android.permission.INTERNET`):** Required to load websites and web content.
- **Network State (`android.permission.ACCESS_NETWORK_STATE`):** Used to detect connectivity changes and optimize network requests.
- **Microphone (`android.permission.RECORD_AUDIO`):** Used exclusively for voice search queries when initiated by the user. Audio data is streamed directly to the Android system speech recognizer and is never recorded or stored by Petal Browser.
- **Camera (`android.permission.CAMERA`):** Used on demand for QR/barcode scanning and camera upload inputs on web pages with explicit user consent.
- **Storage / Media Access:** Used to download files and upload user-selected attachments to web forms.
- **Biometric (`android.permission.USE_BIOMETRIC`):** Used locally on the device to lock/unlock Petal Browser with App Lock. Biometric authentication is handled by Android OS security hardware; biometric keys or fingerprints are never accessed or stored by Petal Browser.

---

## 3. Third-Party Services & Advertising (Google AdMob)

Petal Browser includes Google Mobile Ads SDK (AdMob) to provide non-intrusive, supportive advertising banner support on select overview surfaces:

- **Google AdMob:** Google AdMob may collect and process pseudonymous identifiers (such as the Google Advertising ID / AAID), device model, IP address, performance data, and app interaction diagnostics to serve ads and prevent fraud according to Google's Privacy Policy.
- **AdMob Privacy Policy:** You can review Google's advertising policies and opt-out preferences at [https://policies.google.com/technologies/ads](https://policies.google.com/technologies/ads) and [https://policies.google.com/privacy](https://policies.google.com/privacy).
- **Ad Customization / Consent:** Users can manage advertising preferences, reset their Advertising ID, or opt out of personalized ads at any time via Android device settings (**Settings > Google > Ads**).

---

## 4. Web Engine & Cookies

- **WebView:** The browser operates on top of the Android system WebView (Chromium core).
- **Cookie Control:** You can manage or disable first-party and third-party cookies, clear browsing data, and toggle Incognito / Private browsing modes at any time within **Settings > Privacy & Security**.

---

## 5. Security of Your Data

All personal browser state (bookmarks, tabs, history, passwords, and custom preferences) resides within the application's private, encrypted internal storage sandbox on your device (`/data/data/com.petal.browser/`). Petal Browser implements HTTPS-only mode, DNS-over-HTTPS options, and strict sandboxing to protect your browsing sessions.

---

## 6. Children's Privacy

Petal Browser does not knowingly collect or solicit any personally identifiable information from children under the age of 13.

---

## 7. Changes to This Privacy Policy

We may update our Privacy Policy periodically. Any updates will be published with an updated revision date in this document and accessible via the application repository.

---

## 8. Contact Information

If you have questions, suggestions, or concerns regarding this Privacy Policy or Petal Browser, please contact us:

- **Developer:** Shrey Agarwal
- **GitHub Repository:** [https://github.com/shreyagarwal72/petal](https://github.com/shreyagarwal72/petal)
- **Issue Tracker:** [https://github.com/shreyagarwal72/petal/issues](https://github.com/shreyagarwal72/petal/issues)
