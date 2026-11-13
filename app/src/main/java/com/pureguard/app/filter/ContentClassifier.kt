package com.pureguard.app.filter

/**
 * Lightweight, fully on-device text classifier used on searches, URLs and
 * on-screen text. Strong terms are explicit; weak terms only count in bulk.
 */
object ContentClassifier {

    private val STRONG = listOf(
        "porn", "porno", "pornography", "pornhub", "p0rn", "pr0n", "xxx", "xvideos",
        "xvideo", "xnxx", "xhamster", "redtube", "youporn", "spankbang", "brazzers",
        "hentai", "nhentai", "rule34", "rule 34", "nsfw", "onlyfans", "fansly",
        "chaturbate", "stripchat", "bongacams", "livejasmin", "camsoda", "eporner",
        "motherless", "fapello", "thothub", "erome", "blowjob", "blowjobs", "cumshot",
        "creampie", "gangbang", "deepthroat", "handjob", "milf", "milfs", "camgirl",
        "camgirls", "sexcam", "nudes", "sex video", "sex videos", "sex tape",
        "sex chat", "sexting", "nude pics", "nude photos", "naked girls", "naked women",
        "leaked nudes", "free porn", "jav uncensored", "hardcore sex", "anal sex",
        "pornstar", "pornstars", "xxx videos", "18+ videos", "adult videos",
        "only fans", "lewd", "lewds", "ecchi", "doujin", "futanari", "bukkake",
        "masturbation", "masturbate", "masturbating", "jerk off", "jav porn",
    )

    private val WEAK = listOf(
        "sex", "sexy", "nude", "naked", "boobs", "tits", "titties", "pussy", "dick",
        "cock", "horny", "erotic", "erotica", "fetish", "bdsm", "kinky", "stripper",
        "striptease", "orgasm", "anal", "threesome", "escort", "escorts", "booty",
        "thong", "lingerie", "topless", "bikini", "seductive", "hot girls",
        "busty", "nipples", "slut", "whore", "hookup", "hook up", "onlyfan",
        "uncensored", "xrated", "x rated",
    )

    private val strongSet = STRONG.toHashSet()
    private val pattern = Regex(
        "(?<![a-z0-9])(" +
            (STRONG + WEAK).sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } +
            ")(?![a-z0-9])",
        RegexOption.IGNORE_CASE,
    )

    data class Score(val strong: Int, val weak: Int) {
        val value: Int get() = strong * 3 + weak
    }

    /** Counts *distinct* matched terms, so repetition of one word can't trigger a block. */
    fun score(text: String): Score {
        if (text.isBlank()) return Score(0, 0)
        val hits = HashSet<String>()
        pattern.findAll(text.lowercase()).forEach { hits += it.value }
        val strong = hits.count { it in strongSet }
        return Score(strong, hits.size - strong)
    }

    /** For short, intent-revealing text: a search query, URL, or typed text. */
    fun isAdultQuery(text: String): Boolean {
        val normalized = text.lowercase()
            .replace('+', ' ').replace('_', ' ').replace('-', ' ').replace("%20", " ")
        val s = score(normalized)
        return s.strong >= 1 || s.weak >= 2
    }

    /** For a whole screen of text: require several hits to avoid false positives. */
    fun isAdultScreen(text: String): Boolean {
        val s = score(text)
        return s.strong >= 2 || s.value >= 6
    }
}
