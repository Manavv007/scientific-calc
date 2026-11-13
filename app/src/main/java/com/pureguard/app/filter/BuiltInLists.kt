package com.pureguard.app.filter

/** Hand-maintained rules that complement the large downloaded blocklists. */
object BuiltInLists {

    /**
     * Search engines / video sites rewritten at DNS level to their enforced
     * "safe" endpoints. Key = exact hostname, value = safe hostname.
     */
    val SAFE_SEARCH_EXACT: Map<String, String> = buildMap {
        listOf("www.bing.com", "bing.com", "cn.bing.com").forEach { put(it, "strict.bing.com") }
        listOf(
            "duckduckgo.com", "www.duckduckgo.com", "start.duckduckgo.com",
            "html.duckduckgo.com", "lite.duckduckgo.com", "links.duckduckgo.com",
        ).forEach { put(it, "safe.duckduckgo.com") }
        listOf(
            "www.youtube.com", "youtube.com", "m.youtube.com", "music.youtube.com",
            "youtubei.googleapis.com", "youtube.googleapis.com",
            "www.youtube-nocookie.com", "youtube-nocookie.com",
        ).forEach { put(it, "restrict.youtube.com") }
        listOf("yandex.ru", "www.yandex.ru", "yandex.com", "www.yandex.com", "ya.ru", "www.ya.ru")
            .forEach { put(it, "familysearch.yandex.ru") }
        listOf("pixabay.com", "www.pixabay.com").forEach { put(it, "safesearch.pixabay.com") }
    }

    /** google.com, www.google.co.uk, google.de ... (but not mail.google.com etc.) */
    val GOOGLE_SEARCH = Regex("^(www\\.)?google\\.([a-z]{2,3})(\\.[a-z]{2})?$")
    const val GOOGLE_SAFE = "forcesafesearch.google.com"

    /**
     * Services that exist mainly to get around filters: encrypted DNS
     * resolvers, web proxies, anonymizers and VPN providers, plus image
     * search/translation proxies that are well-known bypass routes.
     * Matching includes all subdomains.
     */
    val BYPASS_DOMAINS: Set<String> = setOf(
        // Encrypted DNS (DoH / DoT) resolvers that would skip the filter
        "dns.google", "dns.google.com", "8888.google",
        "cloudflare-dns.com", "one.one.one.one", "1dot1dot1dot1.cloudflare-dns.com",
        "mozilla.cloudflare-dns.com", "chrome.cloudflare-dns.com",
        "dns.quad9.net", "dns9.quad9.net", "dns10.quad9.net", "dns11.quad9.net",
        "doh.opendns.com", "dns.opendns.com", "doh.familyshield.opendns.com",
        "dns.adguard.com", "dns.adguard-dns.com", "unfiltered.adguard-dns.com",
        "dns-unfiltered.adguard.com", "dns.nextdns.io", "nextdns.io",
        "dns.controld.com", "freedns.controld.com", "controld.com",
        "doh.mullvad.net", "dns.mullvad.net", "adblock.dns.mullvad.net",
        "dns0.eu", "open.dns0.eu", "doh.libredns.gr", "dns.alidns.com",
        "doh.pub", "dns.pub", "doh.360.cn", "dns.twnic.tw", "doh.dns.sb",
        "dns.sb", "dns.switch.ch", "doh.ffmuc.net", "dns.digitale-gesellschaft.ch",
        "dns.rabbitdns.org", "doh.xfinity.com", "dns.brahma.world",
        "odvr.nic.cz", "doh.applied-privacy.net", "dns.njal.la",
        "doh.cleanbrowsing.org", "dns.decloudus.com", "ordns.he.net",
        "doh.tiar.app", "dns.aa.net.uk", "dnsforge.de", "blitz.ahadns.com",
        // Firefox DoH canary: answering NXDOMAIN tells Firefox to disable DoH
        "use-application-dns.net",
        // Apple iCloud Private Relay
        "mask.icloud.com", "mask-h2.icloud.com", "mask-api.icloud.com",
        // Tor
        "torproject.org", "tor.eff.org", "bridges.torproject.org", "onion.ws",
        "onion.ly", "onion.pet", "tor2web.org", "onion.to", "onion.link",
        // Web proxies / unblockers
        "hide.me", "hidester.com", "kproxy.com", "proxysite.com", "croxyproxy.com",
        "croxyproxy.rocks", "croxy.network", "croxy.org", "blockaway.net",
        "proxyium.com", "4everproxy.com", "hidemyass.com", "proxfree.com",
        "whoer.net", "vpnbook.com", "megaproxy.com", "zalmos.com", "unblockvideos.com",
        "genmirror.com", "proxyscrape.com", "proxyorb.com", "plainproxies.com",
        "filterbypass.me", "unblock-websites.com", "steganos.com", "anonymouse.org",
        "hidemy.name", "hidemy.io", "atozproxy.com", "proxy-site.com", "sitenable.com",
        "freeproxy.win", "unblocked.how", "unblocksites.co", "youtubeunblocked.live",
        "translate.goog", // Google Translate page proxy (e.g. site-com.translate.goog)
        // Consumer VPN services
        "nordvpn.com", "expressvpn.com", "protonvpn.com",
        "surfshark.com", "windscribe.com", "tunnelbear.com", "privateinternetaccess.com",
        "cyberghostvpn.com", "hotspotshield.com", "ipvanish.com", "purevpn.com",
        "vyprvpn.com", "mullvad.net", "atlasvpn.com", "privadovpn.com",
        "urban-vpn.com", "betternet.co", "turbovpn.com", "psiphon.ca", "psiphon3.com",
        "lantern.io", "getlantern.org", "ultrasurf.us", "veepn.com", "hola.org",
        "touchvpn.net", "x-vpn.com", "vpn.ht", "browsec.com", "zenmate.com",
        "warp.plus", "zero-trust-client.cloudflareclient.com",
        "engage.cloudflareclient.com", "connectivity.cloudflareclient.com",
    )

    /** Strong tokens: a hostname containing any of these is almost always adult. */
    val DOMAIN_TOKENS: List<String> = listOf(
        "porn", "p0rn", "pr0n", "xxx", "xvideo", "xnxx", "xhamster", "hentai",
        "nsfw", "redtube", "youporn", "spankbang", "brazzers", "onlyfans", "fansly",
        "chaturbate", "stripchat", "bongacams", "camsoda", "livejasmin", "camgirl",
        "sexcam", "sextube", "sexvid", "freesex", "fapello", "thothub", "rule34",
        "nhentai", "eporner", "motherless", "milf", "bdsm", "fetish", "nudes",
        "nudity", "erotic", "hookup", "camwhore", "pornstar", "boobs", "gangbang",
        "cumshot", "blowjob", "jerkmate", "adultfriend", "sugardaddy", "escortservice",
        "xhcdn", "phncdn", "rdtcdn",
    )

    /** Hostnames that would be false positives for the token heuristic. */
    val TOKEN_EXCEPTIONS: List<String> = listOf(
        "essex", "sussex", "middlesex", "wessex", "fetishistic", // only the token check
    )

    /** Domains that must never be blocked (system / connectivity / this app). */
    val NEVER_BLOCK: Set<String> = setOf(
        "connectivitycheck.gstatic.com", "connectivitycheck.android.com",
        "clients3.google.com", "time.android.com", "android.clients.google.com",
        "raw.githubusercontent.com", "github.com", "objects.githubusercontent.com",
    )

    /** Apps that are opened only to bypass filters or are adult apps. */
    val BLOCKED_PACKAGES: Set<String> = setOf(
        // Tor & anonymity
        "org.torproject.torbrowser", "org.torproject.torbrowser_alpha",
        "org.torproject.android", "info.guardianproject.orfox", "com.tor.browser",
        "org.briarproject.briar.android", "org.purplei2p.i2pd", "net.i2p.android",
        // Browsers with a built-in VPN / proxy
        "com.opera.browser", "com.opera.browser.beta", "com.opera.mini.native",
        "com.opera.gx", "com.aloha.browser", "com.epicbrowser.android",
        "com.ucmobile.intl", "com.uc.browser.en", "com.cloudmosa.puffinFree",
        "com.cloudmosa.puffin", "com.cloudmosa.puffinTV", "mobi.mgeek.TunnyBrowser",
        "com.tenta.android", "com.ghostery.android.ghostery",
        // Cloudflare WARP (would replace the DNS filter)
        "com.cloudflare.onedotonedotonedotone",
        // Psiphon / Lantern / Ultrasurf
        "com.psiphon3", "com.psiphon3.subscription", "org.getlantern.lantern",
        "com.ultrasurf.android",
        // Adult content / dating hookups (common ones)
        "com.reddit.frontpage.nsfw", "com.tumblr.nsfw", "com.onlyfans.android",
        "com.fansly.app", "com.chaturbate.android", "com.stripchat.app",
        "com.bongacams.app", "com.livejasmin.app",
    )

    /**
     * Public resolvers whose IPs are routed into the VPN and sink-holed:
     * plain DNS to them gets filtered answers, DoH/DoT to them is reset.
     * Our own family-safe upstreams are intentionally not in this list.
     */
    val BLACKHOLE_V4: List<String> = listOf(
        "8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "1.1.1.2", "1.0.0.2",
        "9.9.9.9", "149.112.112.112", "9.9.9.10", "149.112.112.10", "9.9.9.11",
        "149.112.112.11", "208.67.222.222", "208.67.220.220", "208.67.222.123",
        "208.67.220.123", "94.140.14.14", "94.140.15.15", "94.140.14.140",
        "94.140.14.141", "94.140.14.15", "94.140.15.16", "76.76.2.0", "76.76.10.0",
        "76.76.2.1", "45.90.28.0", "45.90.30.0", "185.222.222.222", "45.11.45.11",
        "223.5.5.5", "223.6.6.6", "119.29.29.29", "180.76.76.76", "77.88.8.8",
        "77.88.8.1", "64.6.64.6", "64.6.65.6", "8.26.56.26", "8.20.247.20",
        "156.154.70.1", "156.154.71.1", "4.2.2.1", "4.2.2.2", "84.200.69.80",
        "194.242.2.2", "194.242.2.3", "193.110.81.0", "185.253.5.0",
        "162.159.36.1", "162.159.46.1", "185.228.168.9", "185.228.169.9",
    )

    val BLACKHOLE_V6: List<String> = listOf(
        "2001:4860:4860::8888", "2001:4860:4860::8844",
        "2606:4700:4700::1111", "2606:4700:4700::1001",
        "2606:4700:4700::1112", "2606:4700:4700::1002",
        "2620:fe::fe", "2620:fe::9", "2620:fe::10", "2620:fe::fe:10",
        "2620:119:35::35", "2620:119:53::53",
        "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff", "2a10:50c0::1:ff", "2a10:50c0::2:ff",
        "2a07:a8c0::", "2a07:a8c1::", "2a0d:2a00:1::", "2a0d:2a00:2::",
    )
}
