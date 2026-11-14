package com.petal.browser.customtabs

import mozilla.components.concept.engine.Engine
import mozilla.components.feature.customtabs.AbstractCustomTabsService
import com.petal.browser.engine.gecko.PetalEngineStore

/**
 * PetalCustomTabsService
 * ─────────────────────────────────────────────────────────────────────────
 * Bound service extending Mozilla Android Components [AbstractCustomTabsService].
 * Enables external third-party apps to discover Petal as an official Custom Tabs provider,
 * establish warm sessions, and speculatively warm up GeckoView.
 */
class PetalCustomTabsService : AbstractCustomTabsService() {
    override val engine: Engine by lazy {
        PetalEngineStore.getEngine(applicationContext)
    }
}
