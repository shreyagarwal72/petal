package com.petal.browser.browser

/**
 * Marker interface for delegates installed by Petal so that re-binding
 * (e.g. during Activity recreation or session switching) never mistakes them
 * for Mozilla's internal engine delegates or double-wraps them.
 */
interface PetalOwnedDelegate
