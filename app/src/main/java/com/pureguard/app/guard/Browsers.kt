package com.pureguard.app.guard

/** Address-bar view IDs of popular Android browsers. */
object Browsers {
    val URL_BAR_IDS: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("com.android.chrome:id/url_bar", "com.android.chrome:id/search_box_text"),
        "com.chrome.beta" to listOf("com.chrome.beta:id/url_bar"),
        "com.chrome.dev" to listOf("com.chrome.dev:id/url_bar"),
        "com.chrome.canary" to listOf("com.chrome.canary:id/url_bar"),
        "com.google.android.apps.chrome" to listOf("com.google.android.apps.chrome:id/url_bar"),
        "org.chromium.chrome" to listOf("org.chromium.chrome:id/url_bar"),
        "com.brave.browser" to listOf("com.brave.browser:id/url_bar"),
        "com.brave.browser_beta" to listOf("com.brave.browser_beta:id/url_bar"),
        "com.microsoft.emmx" to listOf("com.microsoft.emmx:id/url_bar"),
        "com.kiwibrowser.browser" to listOf("com.kiwibrowser.browser:id/url_bar"),
        "com.yandex.browser" to listOf("com.yandex.browser:id/bro_omnibar_address_title_text", "com.yandex.browser:id/suggest_omnibox_query_edit"),
        "org.mozilla.firefox" to listOf(
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "org.mozilla.firefox:id/url_bar_title",
            "org.mozilla.firefox:id/mozac_browser_toolbar_edit_url_view",
        ),
        "org.mozilla.firefox_beta" to listOf("org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view"),
        "org.mozilla.fenix" to listOf("org.mozilla.fenix:id/mozac_browser_toolbar_url_view"),
        "org.mozilla.fennec_fdroid" to listOf("org.mozilla.fennec_fdroid:id/mozac_browser_toolbar_url_view"),
        "io.github.forkmaintainers.iceraven" to listOf("io.github.forkmaintainers.iceraven:id/mozac_browser_toolbar_url_view"),
        "com.sec.android.app.sbrowser" to listOf(
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.sec.android.app.sbrowser:id/custom_tab_toolbar_url_bar_text",
        ),
        "com.sec.android.app.sbrowser.beta" to listOf("com.sec.android.app.sbrowser.beta:id/location_bar_edit_text"),
        "com.duckduckgo.mobile.android" to listOf("com.duckduckgo.mobile.android:id/omnibarTextInput"),
        "com.opera.browser" to listOf("com.opera.browser:id/url_field"),
        "com.opera.mini.native" to listOf("com.opera.mini.native:id/url_field"),
        "com.vivaldi.browser" to listOf("com.vivaldi.browser:id/url_bar"),
        "com.ecosia.android" to listOf("com.ecosia.android:id/url_bar"),
        "com.mi.globalbrowser" to listOf("com.mi.globalbrowser:id/url"),
        "com.huawei.browser" to listOf("com.huawei.browser:id/url_bar", "com.huawei.browser:id/search_text"),
        "com.heytap.browser" to listOf("com.heytap.browser:id/url_bar"),
        "com.coloros.browser" to listOf("com.coloros.browser:id/azt"),
        "com.vivo.browser" to listOf("com.vivo.browser:id/search_text"),
        "com.google.android.googlequicksearchbox" to listOf(
            "com.google.android.googlequicksearchbox:id/search_box",
            "com.google.android.googlequicksearchbox:id/googleapp_search_box",
        ),
    )

    fun isBrowser(pkg: String) = pkg in URL_BAR_IDS
}
