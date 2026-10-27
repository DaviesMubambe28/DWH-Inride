package zm.co.dhwinride.core.data

/** Normalizes common Zambian phone input to E.164 before it is stored or sent. */
object ZambiaPhoneNumber {
    private val allowedCharacters = Regex("^[+0-9\\s().-]+$")
    private val e164Zambia = Regex("^\\+260[0-9]{9}$")

    fun normalize(input: String): String? {
        val value = input.trim()
        if (value.isEmpty() || !allowedCharacters.matches(value) || value.count { it == '+' } > 1) {
            return null
        }

        val firstNonWhitespace = value.firstOrNull { !it.isWhitespace() }
        if ('+' in value && firstNonWhitespace != '+') return null

        val digits = value.filter { it in '0'..'9' }
        val normalized = when {
            firstNonWhitespace == '+' -> "+$digits"
            digits.startsWith("260") -> "+$digits"
            digits.length == 10 && digits.startsWith('0') -> "+260${digits.drop(1)}"
            digits.length == 9 -> "+260$digits"
            else -> return null
        }

        return normalized.takeIf { e164Zambia.matches(it) }
    }
}
