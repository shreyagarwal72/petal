### v1.7.7

- new: Made Google Sans Flex permanent as **Petal Signature** font and added support for loading custom `.ttf`/`.otf` font files directly from device storage.
- new: Introduced custom font tier customizer with independent **Display**, **Headline**, and **Body** level tabs and expressive `PetalSlider` controls for Weight, Width, Optical Size, Slant, and Roundness.
- fix: Updated search bar hint to *"Search or type URL"* across homepage searchbar, omnibox, and widgets.
- fix: Hidden frequently visited shortcuts row on blank home tabs, displaying them only when opening the omnibox from an active website.
- fix: Fixed Java-Kotlin SAM interop for `onDeny` callback in `PetalPermissionDialogBridge` site permission prompts.

### v1.7.6

- new: Reimagined `PetalSearchGlanceWidget.kt` with Material 3 Expressive morphing shape containers (Clover, Scallop, Burst, Cookie) rendered dynamically using `RoundedPolygon` and canvas bitmap drawing.
- new: Integrated `PetalAboutDeveloperSheet.kt` and `PetalAboutDeveloperBridge` featuring expressive hero cards, developer metric badges, tech stack chips, and community hub links.
- fix: Fixed window inset propagation in `PetalTabSwitcherSheet.kt` and `PetalTabGridSwitcher.kt` so `ExpressiveHeader` seamlessly includes status bar top padding.

### v1.7.5

- new: Integrated `Util1DM` utility class to automatically detect 1DM / 1DM+ / 1DM Lite download managers and hand off downloads, headers, cookies, and user-agent without UI changes.
- fix: Resolved Kotlin build errors in `PetalAiHubBridge.kt`, `PetalAiHubScreen.kt`, and `PetalTabGridSwitcher.kt`.

### v1.7.4

- fix: Audited and balanced Compose layout braces in `PetalTabGridSwitcher.kt`, resolving outer `BoxScope.align` and syntax parser cascade errors.
- fix: Created `PetalAiHubBridge.kt`, corrected `Settings_Activity` references in `PetalAiHubScreen.kt`, and aligned `performResearch` callback signature in `PetalSettingsScreen.kt`.

### v1.7.3

- new: Added dedicated **Petal AI & API Keys Hub** category in `PetalSettingsScreen.kt` for configuring Gemini, OpenRouter, OpenAI, Grok, and Groq API keys, model selections, and live connection testing.
- new: Reimagined `PetalAiHubScreen.kt` as the unified home for Petal Native AI (Deep Research, AI search) and web AI tools directory, complete with API key status and 1-tap quick launchers.

### v1.7.2

- new: Upgraded Tab Manager (`PetalTabGridSwitcher.kt`), Chrome Flags (`PetalChromeFlagsScreen.kt`), and Extensions (`PetalExtensionsScreen.kt`) to support Material 3 `ExpressiveHeader`.
- fix: Fixed double status-bar top padding issue on Bookmarks screen (`PetalBookmarksScreen.kt`).

### v1.7.1

- fix: Stored user custom profile picture permanently in `context.filesDir` to prevent deletion when clearing app cache or background tasks (`GoogleAccountManager.kt`).
- new: Built interactive profile picture cropping modal sheet `PetalAvatarCropSheet.kt` with pinch-to-zoom, pan, rotation, and live circular mask preview.

### v1.7.0

- new: Bound `PetalHapticFeedback` to Compose `LocalHapticFeedback` in `PetalTheme.kt` for universal tap feedback across all components.
- fix: Connected `PetalHapticEngine` with Android Accessibility (`TalkBack` Touch Exploration) and OS system haptic settings.
- fix: Added instant vibrator cancellation before triggering new patterns so pattern testing in Settings switches immediately.
- fix: Expanded multi-tiered waveform fallbacks for all haptic patterns on devices lacking hardware primitive composition.

### v1.6.9

- fix: Increased homepage greeting tagline `maxLines` to 4, set `fontSize = 15.sp`, and pruned taglines to prevent ellipsis (`...`) truncation on all screen sizes (`PetalHomeScreen.kt`).

### v1.6.8

- fix: Added `androidx.compose.ui.unit.sp` import to `ExpressiveHeader.kt` for proper `TextUnit` extension resolution.

### v1.6.7

- fix: Resolved Settings unresponsiveness by removing legacy `nestedScroll` connection from `PetalSettingsScreen.kt`.
- fix: Fitted User Accounts header title cleanly onto a single line without truncation across all screen widths (`ExpressiveHeader.kt` & `ChromeAccountSyncScreen.kt`).

### v1.6.6

- fix: Restored synchronous initialization of user profile avatar, name, and preferences on app restart (`BrowserActivity.java` & `AccountViewModel.kt`).

### v1.6.5

- new: Integrated `ExpressiveHeader` across Settings overview and subpages with dynamic category titles and subtitles.
- fix: Resolved double-padding layout issue on Clear Browsing Data page (`PetalDeleteScreen.kt`).
- new: Enforced 1 single line for title and 1 single line for subheading on User Profile & Accounts page.

### v1.6.4

- new: Uniform Material 3 ExpressiveHeader alignment across History, Bookmarks, and Downloads pages.
- new: Chrome-style tab card UI (0.68 aspect ratio, 18dp rounded corners, circular close X button, active accent outline).
- new: Disk-persistent tab preview thumbnails (`petal_tab_thumbnails/{tabId}.png`) that restore on app relaunch.
- fix: Restored OS predictive back edge swipe navigation on web pages via 24dp system gesture exclusion margins.
- new: One-time version update welcome greeting tagline ("Welcome to Petal v1.6.4! 🎉") on home screen after updating.

### v1.5.3

- fix: Improved tab preview thumbnail cache management with safe memory recycling and disk persistence.
- improved: Replaced image cropping with proportional fit scaling to display full uncropped web page previews.
- new: Added Chrome-style bouncy zoom scale-up animation when tapping on tab grid cards.

### v1.5.2

- removed: Removed sync encryption password option from User Profile & Accounts page.
- new: Added support for dedicated app password lock alongside biometric fingerprint authentication options.
- new: Integrated Material 3 Expressive masked shape icons and animations (inspired by Monogram Android & Zenith) for custom app password security.

### v1.5.1

- fix: Solved WebView modal, popup, and carousel black overlay misrendering by explicitly disabling force dark mode & algorithmic darkening.
- new: Added "View All Changelogs" history sheet in App Updates settings (ported from Zenith).
- improved: Upgraded UI containment styling across settings screens to pure Material 3 Expressive UI specifications.

### v1.5.0

- imported: Updated PetalTabGridSwitcher with BackHandler context resolution.
- new: Full Material 3 Expressive UI containment redesign.

### v 24 (WIP)

- improved: default standard profile settings
- improved: AdBlock download
- improved: snackbar with clickable link
- improved: download of blob:/data:-files
- improved: app bar
- improved: search bar
- improved: gesture settings
- improved: menu settings
- improved: search providers
- updated: WIKI + help links in app
- updated: translations


### v 23 Harriet Tubman

*Harriet "Moses" Tubman (1822 – 1913) was an American abolitionist and social activist.*

- fix: Unable to access full tab list #1282
- fix: Hide URL-Bar/Status Bar when Scrolling #1278
- fix: auto hide/show control bar when scrolled up/down #1283
- fix: Highlight entire URL #1269
- fix: Day Mode hides clock, battery and notifications #1270
- fix: Behavior/UI settings crashing #1265
- fix: Update JavaScript alert dialogs #1279
- fix: go back not work when redirect happens #1281
- fix: Cookies Getting Disabled #1273
- fix: Tracking protection needs improvment #1266
- fix: Add ability to view image alt text #1000
- fix: toasts not showing entire URL
- updated: translations
- improved: input of search layouts
- improved: more snackbars instead of dialogs
- removed: redundant settings (autofill, custom javascript)
- removed: StartSite
- new: menu settings


### v 22 Die Edelweißpiraten

- fix: opening UI-settings crashing
- fix: some dialogs closing when clicking profile icon
- fix: profile-icon showing wrong icon
- fix: profile settings
- improved: using of profile button
- improved: redirects control from FastToggleDialog
- improved: layout -> removed tab-button -> opened tabs now in overview
- improved: layout -> using outlined style where possible for better contrast
- new: snackbar instead of dialog on some places
- new: gestures -> open startsite,bookmark, history
- new: option to load default profile on start/always/never (via FastToggleDialog)


### v 21 Astrid Lindgren

- fix: edit text dialogs
- fix: userAgent
- fix: not detecting saved websites in some cases
- fix: not switching to default profile, when going back in history
- fix: Themed icon issue #1251
- improved: moved enable/disable switching to standard profile to top of FastToggleDialog
- improved: theme declaration in manifest
- new: snackbar on download complete


### v 20 Rudi Dutschke

- new: disable webview diagnostic data
- new: set render priority to high
- new: enabling/disabling switching always to standard profile (also in in FastToggleDialog)
- new: enabling/disabling redirects in FastToggleDialog
- new: sort FastSearchDialog alphabetically
- new: some app settings in FastToggleDialog
- fix: removed unused code
- fix: third Party Cookies
- fix: tracking-URL-dialog
- fix: HTTP-dialog
- fix: removed unused strings
- fix: (workaround): Codeberg JavaScript error only on FOSS Browser #1231


### v 19.1 Henry David Thoreau

With this release, it is possible to save settings separate for each website. Look at the WIKI for more Infos: https://codeberg.org/Gaukler_Faun/FOSS_Browser/wiki

- new: save settings per website
- new: Error-dialog, when downloading Blob-files
- new: gestures -> toggle redirects, switch to standard profile, open downloads
- fix: not showing most recent entries in overview in some cases
- fix: removed a lot of old code
- fix: Sequences of Java Session Id's appears in URL #1187
- fix: "show on start" doesn't show start #1206
- fix: f-droid information shows old link to github #1204
- fix: Navigation bar not hidden in fullscreen #1099
- fix: Opening URL using window.open() uses default browser instead of Foss Browser #1165
- fix: Input box blocked by the keyboard in the screen #1207
- fix: Themed Icon Missing #1157
- fix: custom search, when containing "#"
- fix: Update Screenshots On F-Droid #1214
- updated: WIKI
- updated: layout
- updated: settings
- updated: translations
- updated: build libraries
- removed: "Open downloads" popup dialog is annoying #1196
- removed: play audio in background, due to Android limitations
- removed: OLED-theme


### v 18.1 "Die rote Zora"

With the release of Android 15 Google introduced a feature called "window insets". Because of that feature there were many layout issues, mainly concerning the system navigation bar. I had to rewrite some layouts. The list of opened tabs, the overview and the search are moved in sidebars.

- layout changes
- updated translations
- some minor code improvements
- fix: tab issues


### v 17 "Michail Alexandrowitsch Bakunin"
- upgrade: Android target SDK
- upgrade: dependencies
- update: translations
- fix: redirecting urls
- fix: minor layout issues
- fix: open links in background #1184
- fix: link on readme.md (thanks to @[Artemka](https://codeberg.org/Artem13327))
- fix: wrong menu contents in landscape orientation #1186
- fix: rename Disable Profiles -> Custom #1178
- fix: Update README #1176
- fix: “Close Tab” icon too small #1156
- fix: crash when clicking overflow with empty URL
- fix: crash with missing notification permission
- fix: Themed Icon Missing #1157
- new: switched from deepl to fairtranslate
- new: edit custom redirects

### v 16 "Alexander Berkmann"
- new: tracking-URL-popup
- fix: context menu in tab overview
- fix: onRenderProcessGone
- fix: http-dialog
- fix: dialogs with long file names
- fix: tab-layout in overflow menu
- updated: translations

### v 15 "Kabale und Liebe"
- new: auto deny cookie-banners (@https://github.com/woheller69/browser)
- new: sort startsite and history by domain
- new: highlight domains in lists and menus
- new: highlight search term
- fix: layout and colors
- fix: Error downloading blob-files (@https://github.com/woheller69/browser)
- fix: Downloading files that requires Referer header #950
- fix: Display errors in dark theme? #1143


### v 14 "Katjuscha" (Protagonistin in Leo Tolstois Auferstehung)
- fix: redirect custom urls
- fix: Back navigation is flawed #1121
- fix: Open in default app #1134
- fix: App crashes when opening settings #1128
- fix: layout/UI improvements
- fix: Deleting duplicated bookmark deletes both #1130
- fix: v13 system theme - colors [re: 1105; 1119] #1126
- fix: profiles -> wildcard support #1133
- fix: Suggestion: Long-press on bookmark icon to save #1140
- fix: Add initial intro #1129
- fix: Splash screen should adapt to theme #1142
- new: simplified receiving text dialog
- updated: translations
- updated: libraries


### v 13 "Friedrich Hecker"
- switched to Google rounded Material You Icons
- fix: improvements in backup/restore
- fix: improvements in custom redirects
- fix: layout improvements
- fix: open deepl.com in normal tab
- fix: lint issues
- fix: theme issues #1105
- fix: WIKI-links
- fix: cleanup strings
- fix: updated translations
- fix: backup on exit
- fix: crashes related to cookie manager #1118 #1111 #1115
- fix: tabs gets multiplied #1117
- fix: Add Initial intro screen #1129
- new: icons in overview menus
- new: fast search on websites (long press search icon in toolbar)
- new: AdBlock -> add custom domains

### v 12 "Gandhi"
- fix: Open Downloads after download complete
- fix: Custom search engine UI/UX #1113
- fix: Cannot import custom Bookmarks #1109
- fix: open links in background
- fix: notification permissions
- fix: infinite loop verification on gitlab.com #1100
- new: backup on exit (optional)
- new: fast search on websites (long press search icon in toolbar)


### v 11 "Bertha von Suttner"
- new: third party cookies control
- new: stop-loading on progress indicator click
- fix: Writing to clipboard from JavaScript doesn't work #853
- fix: recreation of activity on ui-mode change
- fix: translate text from context menu with deepL
- fix: crash on start (A14)
- updated: translation
- updated: layout
- updated: build libraries


### v 10.0 "Clara Zetkin"
- moved repo from Github zu Codeberg
- moved from crowdin to weblate
- fix: cookie/profile settings lost on tab change #1095
- fix: profile settings lost, when going back in history
- fix: Not really a bug... #1075
- fix: Menu Dialog tweaks #1086
- fix: error warnings #1092 and #1082
- fix: removed "open link with" from menus, since it does the same like sharing #1089
- fix: HTTPS or HTTP dialog only asked one time per session #1083
- fix: Gestures: added gestures for "Open Settings" and "Quit app" #1087
- fix: Custom Redirects to sites that don't start with "www." #1097
- fix: lint issues
- new: translate text from context menu with deepL
- updated translations


### v 9.11 "Traven"
- new: context menu in tab overview
- new: icons in menus
- new: delete entries from search
- new: http-warning before loading
- fix: layout in menus
- fix: menus
- fix: onReceivedHttpAuthRequest password showing #1069
- fix: download names #1071


### v 9.10 "Durruti"
- new: Clipboard record preference thanks to @Bnyro
- new: show url on link menus or when clicking menu title
- new: show urls in lists
- new: tab number in overview
- fix: refactored settings
- fix: layout issues on small devices #976
- fix: Password field for protected sites shows entries #1039
- fix: youtube redirects
- fix: onReceivedError
- fix: Settings refactor thanks to @Bnyro
- fix: OLED theme issues #1052


### v 9.6
- removed: redirection for Instagram
- fix: Redirect error from YouTube #991
- fix: loading images fit screen width
- fix: cookie manager, when going back in history
- fix: buttons not reachable on some dialogs
- fix: Auth-request dialog
- fix: download dialog
- fix: startsite is now the wiki #997
- fix: crash when open downloads, while default file manager not installed
- fix: issues with the file name when downloading thanks to @Bnyro
- fix: kill the app on pressing quit thanks to @Bnyro
- fix: theme settings
- fix: suppress DRM notice #1019
- fix: profile icons - red for attached profiles #999
- fix: Youtube videos in full screen mode cropped #1015
- fix: Twitter-to-Nitter redirector matches some unrelated urls #1022
- fix: & not escaped in search URL #959
- fix: lint issues
- new: circular progress - press to stop loading
- new: Swedish translation
- new: material dialogs in the preferences thanks to @Bnyro
- new: monochrome icon for Android 13+ thanks to @Bnyro
- new: custom redirects thanks to @Bnyro
- new: Custom error pages  thanks to @Bnyro


### v 9.5
- fix: can't open URL from outside, if it was closed before
- fix: Active button colors on OLED theme #1006
- new: hebrew translation


### v 9.4
- new: "post on website" in sharing menu
- new: Serbo-Croation translation
- new: Support app split screen #937
- fix: Restored functionality to proceed according to user selection #941
- updated: translations
- updated: build libraries
- removed: swipe to reload -> use gestures instead

### v 9.3.1
- fix: Half screen after orientation change #919
- fix: not all menu items accessible in landscape orientation
- fix: Download dialog does not decode multibyte file name #917
- fix: File name is not shown in the download prompt #916
- fix: Browser crashes when switching embedded video to full screen #925
- fix: Persistent swipe issues #924

### v 9.2
- fix: crash when sharing links #883
- fix: no password field on webserver authentication #891
- fix: some swiping issues #899 by Lakjdf
- fix: support for intent scheme URLs again #898 by Lakjdf
- fix: refactored code to make it much more readable #896 by T8RIN
- fix: share link
- fix: Home-screen links improvment #906
- fix: no password field on authentication dialog #891
- updated: layout and user experience
- updated: settings for night view
- updated: settings for gestures
- updated: settings for editing profiles #909
- updated translations: *Now 21 languages are fully supported!*

### v 9.1
- fix: removed unnecessary toasts in fastToggle
- fix: Browser crash on search with "%" #861
- fix: progressbar visible when searching
- fix: UI of FastToggleDialog #872
- fix: UI of edit/save as dialog #873
- fix: https warning #863
- fix: Images not displayed #881
- new: gesture to copy link to clipboard #868
- new: copy link to clipboard in context menu #878
- new: switched license to APGL
- new: redirect YouTube/Instagram/Twitter links
- new: show number of tabs in overview

### v 9.0
- fix: popup menu in Overview
- fix: searchbar not hiding in back pressing #852
- fix: edit favorites layout not scrolling #854
- fix: open external link shows Overview #858
- updated: translations

### v 8.9
- fix: some webpages show half the screen #817
- fix: Shortcut on the home screen #830
- fix: crash on first start #829 #820
- fix: Settings button does not open settings #823
- fix: cannot connect to some webservices #818
- fix: default user-agent is now received from installed webview
- fix: long press actions on toolbar buttons #866
- fix: https warning #863
- fix: Images not displayed #881
- updated: translations
- new: moved tab-dialog in overview
- new: permanent night mode (optional) #825
- new: help buttons in most important UI-elements

### v 8.8
- new: Wiki integrated in settings and menus
- new: notification when playing audio on background #800
- new: gestures
- fix: mailto and other intents not working
- fix: button "reload " is hidden #803
- fix: play audio in background #800

### v 8.7
- fix: dark theme in settings activities
- fix: night mode (webView)
- new: play audio on background
- new: Quick toggle to keep screen on
- new: gesture settings for long pressing
- new: open links in background
- new: swipe through menu
- updated: F-Droid (screenshots, description)
- updated: Privacy Policy
- updated: translations (17 languages supported!)
- improved: MaterialYou theme
- improved: search in history, bookmarks, ...
- improved: close open tabs dialog after making selection

### v 8.6
- skipped -

### v 8.5.1
- fix: camera issue #729
- fix: profile icon in toolbar #728
- fix: cookie settings when switching tab
- fix: stay at night mode, when openeing bookmark
- fix: close open tabs window after making selection #746
- new: restart and reload tabs
- new: Material You Design #726
- new: OLED dark theme Feature #742
- new: FOSS Browser in context menu of marked text #723
- new: remember night mode per site #669
- new: adding "copy link" button to download dialog #733
- new: remember night mode, when opening new tabs
- new: gesture: open start site
- new: keep screen on
- improved: set favicons (thanks to @woheller69)
- updated: translations
- updated: build libraries

### v 8.4
- new: camera use (thanks to @woheller69)
- new: microphone use
- new: webRTC support
- new: DRM protected video playback (thanks to @woheller69)
- new: support of encrypted backups (thanks to @woheller69)
- new: profiles instead of whitelists
- new: restore tabs on restart (optional)
- new: restore tabs when killed by system (optional) (thanks to @woheller69)
- fix: AutoComplete TextView (thanks to @woheller69)
- updated: setting screens

### v 8.3
- fix: Swipe to reload not working sometimes #654
- fix: Restoring bookmarks does not work properly #653
- fix: some force closes #642 #643
- fix: gestures and text-edit-scrolling collide #633
- fix: Toolbar hides, but reappears if page is refreshed #660
- fix: minor fixes and UIredirects webview
- new: save dektopMode, Javascript, domStorage also for history items
- removed: PlayStore support
- updated: translations

### v 8.2
- fix: chip desktop, javascript, DOM should not be visible in settings when editing filter names (thanks to @woheller69)
- fix: Sometimes Favicon is stored several times in database (thanks to @woheller69)
- fix: permission problems
- fix: download problems
- new: edit startSite and history items
- new: Privacy Enhancement #602 (thanks to @woheller69)
- new: choose preferred theme
- updated: Edit filter names (thanks to @woheller69)
- updated: backup preference

### v 8.1
- new: edit bookmark-url (thanks to @woheller69)
- new: Reload on swipeDown when at top of webpage (thanks to @woheller69)
- new: Added GlobalPrivacyControl (thanks to @woheller69)
- new: Remove device info from useragent, use prefixes like DuckDuckGo browser (thanks to @woheller69)
- new: favicons in bookmarklist (thanks to @woheller69)
- new: option to select blocked content
- updated: do not track (thanks to @woheller69)
- updated: translations
- fix: sorting of tabs in overview
- fix: DOM-storage description in settings
- fix: apply settings on back/forward navigation
- fix: Ad-Blocking also blocks "social" media
- fix: open link externally

### v 8.0
- new: toggle mobile/desktop mode (thanks to @woheller69)
- new: auto update of AdBlock-hosts (thanks to @woheller69)
- new: save desktop mode/javascript/DOM content settings in bookmarks (thanks to @woheller69)
- new: show colored bookmark icon when editing bookmark category (thanks to @woheller69)
- new: Use settings stored in bookmark when a bookmark is found in search (thanks to @woheller69)
- new: Show icon source of item (start page, bookmarks, history) in search (thanks to @woheller69)
- new: save form data/use autofill service in settings
- fix: correct icon colors when importing bookmarks (thanks to @woheller69)
- fix: content hidden by toolbar
- fix: opening new tabs on older Android versions
- fix: Dark mode not applied to all tabs
- fix: toolbar input handling

### v 7.5
- new: right to left layout support (thanks to @M3hdiRostami)
- new: Farsi (Persian) language support (thanks to @M3hdiRostami)
- updated: AdBlock
- updated: AppIcon
- fix: New bookmarks are not shown in red-filtered bookmark view
- fix: Application crash when trying to watch video in full screen mode
- fix: showing keyboard when leaving fullscreen mode
- fix: interaction with url in address line
- fix: tab handling
- fix: toolbar input handling
- even more fixes

### v 7.4
- removed: app shortcuts
- fix: keyboard not showing on large input fields
- fix: app crash when displaying overview on start
- fix: app crash when showing some dialogs
- fix: clearing data
- fix: open fallback urls
- fix: crashing on fullscreen on older Android versions

### v 7.3
- updated: translations (now 16 languages are supported!)
- updated: build and theme libraries
- updated: AdBlock-hosts
- updated: Material Components
- updated: settings activities
- updated: tab handling
- removed: multi-window support
- removed: theme settings
- new: support of: open target="_blank"
- fix: Opting out of metrics collection
- fix: export bookmarks
- fix: tab handling
- fix: apply javaScript, cookies and DOM content whitelists
- fix: switching between system day/night mode
- fix: dialogs in landscape
- fix: alphabetical sorting of overview entries
- fix: search in toolbar
- fix: many other small fixes

### v 7.2
- fix: zooming on websites
- fix: opening new tab when restarting
- new: close search on site by back pressing

### v 7.1
- removed: ability to save password within bookmark
- updated: AdBlock hosts
- updated: translations
- updated: bookmark management (sorry for removing password saving)
- updated: overview, menus and dialogs
- fix: searx.me search
- new: hide overflow button
- new: whitelist for DOM content
- new: backup/restore bookmarks as html
- many stability improvements -> removed ca. 5000 lines of unnecessary code!)

### v 7.0
- new: F-Droid description
- updated: many translations (BIG THANKS TO ALL CROWDIN translators)
- fix: leave video fullscreen with back key
- fix: create windows inside webView

### v 6.9
- fix: custom search engine
- new: Italian translation
- new: custom user agent
- new: removed storage permission for Android 10+
- updated: some translations
- updated: Android libraries

### v 6.8
- new: search in all overview entries
- new: Blank target href open a new tab
- new: removed device model and build number from user agent string
- new: follow system theme
- new: Amoled theme
- new: moving to AndroidX libraries
- new: favorite settings
- updated: handling of ssl-errors
- updated: adblock hosts
- updated: themes
- updated: Turkish translation
- updated: Czech translation
- updated: Brazilian translation
- updated: Russian translation
- updated: settings (especially filter settings)
- updated: saving locations of backups and screenshots
- removed: tinting of toolbar
- removed: open links in background

### v 6.7
- new: backup and restore settings
- fix: saving on startsite
- fix: toolbar not showing title
- fix: making backups

### v 6.6
- fix: storage permission problems
- fix: location permission problems
- fix: lint issues
- fix: sort startsite by time
- fix: favorite site not loading on start
- fix: inputs not loading
- new: add to startsite from link context menu

### v 6.5
- new: more font sizes (thanks @pbui)
- new: ECOSIA search engine
- new: open dialogs always expanded
- fix: exclude notifications from recent apps
- fix: some popup dialogs not opening
- fix: hide nav button

### v 6.4
- new: FAQ site (thanks @HarryHeights)
- new: long click on tab preview to close
- new: add shortcuts to home screen (long press overview entry)
- new: dynamic shortcuts: two last opened websites
- updated: help dialog
- updated: Portuguese translation (thanks @smarquespt)
- updated: French translation (thanks @franco27)
- updated: adblock domain list
- improved: Overview
- improved: rendering speed
- fix: file upload
- fix: input box not showing on some devices
- fix: hide navigation bar in fullscreen mode
- fix: can't go back in history in some cases

### v 6.3
- new: Add Cookies support for download function
- new: option to enable Save-Data header (thanks @SkewedZeppelin)
- new: set blank start site
- new: fast scroll on long lists
- new: show/hide tab preview
- new: animations when showing/hiding views
- new: Qwant search engine (thanks @Tobiplayer3)
- new: option to open new tab instead of exiting app
- new: Ukrainian translation (thanks @Roman Babiy)
- new: Turkish translation (thanks @ali-demirtas)
- fix: UI and minor issues
- updated: long press menu on websites
- updated: Help dialog
- updated: Polish translation (thanks @gh-pmjm)
- updated: Dutch translation (thanks @Vistaus)
- updated: Taiwan Trad. Chinese translation (thanks @elmru)
- updated: Portuguese translation (thanks @smarquespt)
- updated: Overview dialog
- updated: FastToggle dialog

### v 6.2
- new: advanced gesture settings
- new: show overview on start
- fix: light settings theme

### v 6.1
- new: overview instead of startPage
- new: order and filter bookmarks
- new: edit url of bookmark
- new: open favorite website on start
- new: Code of conduct site (thanks @HarryHeights)
- new: Privacy declaration (thanks @HarryHeights)
- updated: adBlock hosts list
- updated: help dialog
- updated: French translation (thanks @Hellohat)
- updated: Portuguese translation (thanks @smarquespt)
- removed: loginData (use bookmarks instead)
- fix: upload files
- fix: not clearing indexed databases on exit
- many more fixes and improvements

### v 6.0
- updated: adBlock hosts list
- updated: Polish translation (thanks @gh-pmjm)
- fix: problems with characters like ä, ü, ö
- new: Taiwan Trad. Chinese translation (thanks @elmru)
- new: Italian translation (thanks @EnricoMonese)
- new: Portuguese translation (thanks @Sérgio Marques)
- many more fixes and improvements (thanks @BO41)

### v 5.9
- updated: Chinese translation (thanks @YC L)
- updated: Russian translation (thanks @Vladimir Kosolapov)
- new: Polish translation (thanks @gh-pmjm)
- new: Dutch translation (thanks @gHeimen Stoffels)
- new: adaptive icon

### v 5.8
- new: "do not track" header
- new: + button in tab preview
- new: save websites as PDF
- new: show link in context menu
- fix: not applying cookie whitelist when switching tabs
- fix: scrolling issue
- fix: screen rotation issues
- updated: UI + dialogs
- updated: Spanish translation (thanks to @Herman Nuñez)
- updated: Chinese translation (thanks to @smallg0at)

### v 5.7
- new: delete indexed databases and local web storage
- new: Spanish translation (thanks to Herman Nunez)
- new: confirmation dialog before making backup
- new: delete separate lists (Startpage, history, ...)
- new: show unsecured connections and try reloading secure
- new: search engines (Startpage DE, Searx)
- new: notification when download or screenshot complete
- new: block DOM content
- improved Chinese translation (thanks: lishoujun)
- removed: Snackbar (replaced with toasts)
- removed: request desktop site
- removed: build in file manager
- fix: some strings (thanks: gr1sh)
- fix: some urls opening search results

### v 5.6
- new: increase font size
- new: close all websites from notification
- new: desktop mode in "Fast toggle dialog"
- improved: link sharing
- improved: light theme
- improved: UI
- fix: some force closes

### v 5.5
- new: option to disable confirmation dialogs on exit (thanks: element54)
- improved: light theme
- improved: startpage
- fix: dark background on some websites
- fix: ok button on "Fast toggle dialog"
- fix: toolbar not showing title
- fix: back handling

### v 5.4
- new: new app name "F|L|OSS Browser"
- new: dark and light theme
- new: startpage with all contents
- new: full support for cookie whitelist
- new: more options for fullscreen browsing
- new: more options visibility Navigation Button
- new: night mode only while browsing
- updated: help dialog
- removed: different light themes
- fix: double entries in history
- fix: toolbar does not work on start
- fix: keyboard issues in some cases (thanks: element54)

### v 5.3.1
- new: initial support for cookie whitelist (fast toggle)

### v 5.3
- improved: tab opening/removing
- improved: back handling
- improved: enter/exit fullscreen
- other improvements and bug fixes

### v 5.2
- new: decide which tab to open on start
- new: backup complete data and settings
- new: double tap "Navigation Button" to hide
- new: option to disable history (settings and fast toggle)
- new: option to hide/show "Navigation Button"
- new: change position of "Navigation Button"
- new: moved menus in bottom dialog
- new: Baidu as default search engine for Chinese users
- new: Indonesian translation (thanks: Secangkir Kopi)
- changed: backups on root of sd-card
- updated: Help dialog (ENG, DE)
- improved: applying settings from fast toggle dialog
- improved: Chinese translation (thanks: lishoujun)

### v 5.1
- new: Chinese translation (thanks: lishoujun)
- new: close current tab in menu
- new: share text to browser
- improved: hide/show toolbar
- fix: night mode (now in UI settings)
- fix: force close when clicking direct download link
- fix: screenshot, when started via holder service
- fix: some strings
- fix: possible force close on Android > Nougat

### v 5.0

With this update "Ninja Browser" is used as base for "Browser". The 
concept remains the old: simple but powerful with a nice looking user interface. 
Main advantage of this step are a better implementation of tabs. Now you can open 
as many tabs as you wish. Also you have a new startpage. Please read the new "Help dialog" 
for more information.
    
- full oreo support

### v 4.5
- new: Chinese translation (Thanks: Jumping Yang)
- new: keyboard go button actions
- new: settings icon in toolbar (click for toggle/long click for main settings)
- new: possibility to temporally hide navigation arrows
- new: auto fill (authentication dialog)
- improved: tab behavior
- fix: load default settings on start
- fix: force close when sharing screenshot
- fix: not showing website title

### v 4.4
- fix: save screenshot
- fix: saving entries containing an apostrophe
- fix: option to restart app to apply swipe gesture
- new: http basic authentication
- improved: behavior of tab preview/behavior

### v 4.3
- fix: title of setting subpages
- fix: crash when selected list at startup
- new: swipe to switch tabs optional (settings/popup settings)

### v 4.2
- removed: backup/restore of passStorage
- fix: hiding keyboard after searching in lists
- fix: history - scroll to latest entry
- improved: code and UI
- new: swipe to switch tabs
- new: settings on different subpages
- new: auto orientation (sensor/device)
- new: offer restart to apply some settings
- new: theme support

### v 4.1
- removed: offline support (use screenshot for saving)
- removed: unnecessary permissions
- fix: reset menu after editing file name
- fix: overwriting of files while renaming
- fix: several code improvements
- fix: delete passStorage
- improved: layout close button
- improved: bundled Notifications
- new: Google encrypted search (override AMP-links)
- new: advanced backup/restore data

### v 4.0
- removed: donation link on Github
- removed: video thumbnail
- improved: file manager (icons, image preview)
- new: scroll to end of website arrow

### v 3.9
- removed: notification actions (to buggy)
- removed: direct opening a donation website&lt;br>
- fix: notification behavior (open multiple links)
- fix: force close on toolbar click
- fix: website partially not visible
- improved: refresh website (long click "history icon")
- improved: help dialog (menu -> more -> Help)
- new: switched to Picasso library to load images
- new: homepage setup (bookmark list)
- new: open links directly (optional)
- new: moved from activities to fragments
- new: bookmarks, readLater, history as startpage (settings)

### v 3.8
- removed: swipe to refresh
- fix: some random force closes
- fix: notifications on Lollipop/Marshmallow devices
- fix: do not scroll to list end after editing entry
- improved: taking screenshots
- improved: long click menu in lists
- new: menu -> reload website
- new: Russian translation
- new: show current url when starting editing mode in toolbar
- new: close tabs from tab preview
- new: set empty page as favorite

### v 3.7
- new: changelog after update
- new: long click on toolbar opens tab overview
- new: click on toolbar opens search/enter URL
- fix: toolbar swipe (lists)
- fix: hide navigation buttons
- fix: contributor title
- fix: always loading homepage when closing settings
- fix: cancel search on site
- improved: cookie settings
- improved: open links in new tab

### v 3.6
- new: always hide statusbar on fullscreen videos
- new: five tabs (@CGSLURP LLC)
- new: toolbar and button animations
- new: toolbar gestures in lists
- new: save link destination
- fix: download pictures
- fix: startpage on javascript whitelist
- improved: portrait layout
- improved: Adblock integration
- improved: Desktop site request
- improved: sorting of lists
- improved: code simplified
- improved: UI (colors, strings, layouts)
- updated: help dialog, introscreen

### v 3.5
- fix: keyboard issue
- new: request desktop site (@CGSLURP LLC)
- new: adblocker (@CGSLURP LLC)

### v 3.4
- improved: sorting of list entries
- fix: keyboard not open (landscape)

### v 3.3
- removed: swipe to navigate
- improved:opening urls
- fixed: pin screen not showing

### v 3.1 - v 3.2
- fix: possible crash an Android < Lollipop when opening settings
- fix: search from toolbar
- fix: crash when website title contains apostrophe

### v 3.0
- fix: F-Droid build error
- lint: disable missing translation
- updated: support libraries

### v 2.8 - 2.9
- new: video thumbnails
- fix: open links from readLater list (opened from notifications)
- fix: error message on start

### v 2.7
- fix: directory up icon
- fix: saving all entries lowercase

### v 2.4 - 2.6
- new: new file manager
- new: sub menus
- removed: toolbar long click
- improved: toolbar in lists
- improved: removed unnecessary code
- fix: sort readLater by date
- fix: sort by title (needs uppercase on first letter in the entries)
- fix: double entries in history
- fix: app icon color
- fix: open shortcuts
- fix: open links from notification
- fix: delete data on exit

### v 2.3
- new: search in lists
- new: no duplicate bookmarks
- improved: UI (lists, colors, buttons)
- improved: database model

### v 2.2
- new: about screen
- new: intro screen
- fixed: save website (passStorage): set title
- fixed: notification not dismissed

### v 2.1
- new: bundled notifications

### v 2.0
- improved: encryption of passStore databases
- fixed: F-Droid build error

### v 1.9
- new: encryption of passStore databases

### v 1.8.1
- fixed: f-droid build failure

### v 1.8
- improved: pin screen layout
- improved: sort dialog behavior
- improved: wsitched to xml icons
- removed: bookmark screen
- new: second tab
- new: set bookmark as start site (long click)
- new: cancel button on some dialogs
- new: cancel dialog when clearing whitelist
- fixed: force close (links without "/")

### v 1.7:
- fixed: F-Droid build error

### v 1.6:
- fixed: some strings
- improved: backup and restore databases

### v 1.0+ (first public release):
- improved: readLater notification (v1.1)
- improved: search results in German
- improved: settings screen
- improved: license dialog
- improved: minor ui/code tweaks
- new: websearch (from shared text)
- new: donate (settings)
- new: changelog (settings) (v1.4)
- new: sort lists by title and date (v1.5)
- fixed: navigation settings (v1.2)
- fixed: screenshot (v1.3)