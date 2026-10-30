package com.kanjidodia

/** Romanização Hepburn simples de leituras em kana ("ニチ" -> "nichi", "なが(い)" -> "naga(i)"). */
object Romaji {
    private val BASE = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'ゐ' to "i", 'ゑ' to "e", 'を' to "o", 'ゔ' to "vu",
    )
    private val SMALL_Y = mapOf('ゃ' to "a", 'ゅ' to "u", 'ょ' to "o")
    private val SMALL_VOWEL = mapOf('ぁ' to "a", 'ぃ' to "i", 'ぅ' to "u", 'ぇ' to "e", 'ぉ' to "o")

    fun of(kana: String): String {
        val out = StringBuilder()
        var geminate = false
        var pendingN = false
        for (raw in kana) {
            // Katakana -> hiragana (mesmo bloco, deslocado em 0x60)
            val c = if (raw in 'ァ'..'ヶ') raw - 0x60 else raw
            val syllable = BASE[c]
            when {
                c == 'っ' -> { geminate = true; continue }
                c == 'ん' -> { flushN(out, pendingN, null); pendingN = true; continue }
                c in SMALL_Y && out.endsWith("i") -> {
                    out.setLength(out.length - 1)
                    if (!(out.endsWith("sh") || out.endsWith("ch") || out.endsWith("j"))) out.append('y')
                    out.append(SMALL_Y.getValue(c))
                }
                c in SMALL_VOWEL && out.isNotEmpty() && out.last() in "aiueo" -> {
                    out.setLength(out.length - 1)
                    out.append(SMALL_VOWEL.getValue(c))
                }
                c == 'ー' && out.isNotEmpty() && out.last() in MACRON -> {
                    out.setCharAt(out.length - 1, MACRON.getValue(out.last()))
                }
                syllable != null -> {
                    flushN(out, pendingN, syllable)
                    pendingN = false
                    if (geminate) out.append(if (syllable.startsWith("ch")) 't' else syllable[0])
                    out.append(syllable)
                }
                else -> {
                    flushN(out, pendingN, null)
                    pendingN = false
                    out.append(SMALL_Y[c] ?: SMALL_VOWEL[c] ?: c.toString())
                }
            }
            geminate = false
        }
        flushN(out, pendingN, null)
        return LONG_VOWELS.fold(out.toString()) { s, (from, to) -> s.replace(from, to) }
    }

    private val MACRON = mapOf('a' to 'ā', 'i' to 'ī', 'u' to 'ū', 'e' to 'ē', 'o' to 'ō')

    // Vogais longas com mácron ("kou" -> "kō"); "ii" e "ei" ficam como estão, como no Hepburn.
    // O okurigana entre parênteses separa as vogais, então "o(u)" não vira "ō".
    private val LONG_VOWELS = listOf("ou" to "ō", "oo" to "ō", "uu" to "ū", "aa" to "ā", "ee" to "ē")

    /** "ん" vira "n", com apóstrofo antes de vogal ou "y" para não confundir (ex.: "kan'i"). */
    private fun flushN(out: StringBuilder, pending: Boolean, next: String?) {
        if (!pending) return
        out.append('n')
        if (next != null && next[0] in "aiueoy") out.append('\'')
    }
}
