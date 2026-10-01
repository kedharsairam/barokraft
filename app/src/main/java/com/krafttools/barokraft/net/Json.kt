package com.krafttools.barokraft.net

/**
 * A minimal JSON reader.
 *
 * ## Why not a library
 *
 * Two reasons, and the second is the one that matters.
 *
 * The first is size. This app has one HTTP endpoint and one response
 * shape. A general JSON library is several hundred kilobytes of code to
 * walk an object of parallel arrays.
 *
 * The second is that **a weather API's `null` is data, and a general
 * parser makes it easy to lose.** `precipitation_probability` comes back
 * `null` for every model without an ensemble. The wrong handling is to
 * default it to zero, which then renders as "0% chance of rain" — a
 * confident, false statement produced by a missing value. This reader
 * distinguishes absent from zero from present, and
 * [JsonValue.asFloatOrNull] is the only way to get a number out, so a
 * `null` cannot become a zero by accident.
 *
 * ## What it supports
 *
 * Objects, arrays, strings, numbers, booleans, `null`. That is the whole
 * of JSON that the API returns. Escapes are handled in strings. Numbers
 * keep their text form so a value that arrived as `1013.2` is not
 * round-tripped through a `Double` and back.
 */
sealed class JsonValue {

    object Null : JsonValue()

    data class Bool(val value: Boolean) : JsonValue()

    /** The number, and the text it arrived as. */
    data class Num(val value: Double, val raw: String) : JsonValue() {
        /**
         * This number as a `Long`, parsed from the text it arrived as.
         *
         * [asFloatOrNull] is wrong for anything that identifies a moment
         * in time. A `Float` holds integers exactly only to 2^24, and
         * epoch seconds are around 1.76e9 — so `1_759_264_800f.toLong()`
         * is **32 seconds early**, and every hour label is then wrong by
         * half a minute, accumulating into visibly wrong times across a
         * week-long forecast.
         *
         * Reading the raw text sidesteps it entirely: the digits are
         * already an integer and nothing is rounded on the way through.
         */
        fun asLongFromRaw(): Long? = raw.toLongOrNull() ?: value.toLong()
    }

    data class Str(val value: String) : JsonValue()

    data class Arr(val items: List<JsonValue>) : JsonValue()

    data class Obj(val fields: Map<String, JsonValue>) : JsonValue() {
        operator fun get(key: String): JsonValue = fields[key] ?: Null
    }

    /**
     * The value as a float, or null when it is absent, JSON null, or not
     * a number.
     *
     * This never returns 0 for a missing value. That is the whole point.
     */
    fun asFloatOrNull(): Float? = when (this) {
        is Num -> value.toFloat()
        else -> null
    }

    fun asIntOrNull(): Int? = asFloatOrNull()?.toInt()

    /**
     * This value as a `Long`, for timestamps.
     *
     * Goes through the raw text rather than a `Float`. See
     * [Num.asLongFromRaw] for why that matters: a `Float` is 32 seconds
     * wrong at epoch-second magnitude, which is a bug that produces
     * plausible-looking times and is therefore very hard to spot by eye.
     */
    fun asLongOrNull(): Long? = when (this) {
        is Num -> asLongFromRaw()
        else -> null
    }

    fun asStringOrNull(): String? = (this as? Str)?.value

    /** The array's items, or an empty list when this is not an array. */
    fun asArray(): List<JsonValue> = (this as? Arr)?.items ?: emptyList()

    fun asObject(): Obj = this as? Obj ?: Obj(emptyMap())
}

/** A JSON syntax error, with the offset where it was found. */
class JsonSyntaxError(message: String, val offset: Int) :
    Exception("$message at offset $offset")

/**
 * Parse a JSON document.
 *
 * @throws Protocol.MalformedResponse on a syntax error. A partial parse is
 *   never returned: a forecast with three of seven days and no error
 *   shown is worse than a failed fetch, because the user cannot tell the
 *   difference between "it is not going to rain on Thursday" and "we
 *   lost Thursday".
 */
fun parseJson(text: String): JsonValue {
    val parser = JsonParser(text)
    val value = parser.parseValue()
    parser.skipWhitespace()
    if (!parser.atEnd()) {
        throw Protocol.MalformedResponse("trailing content after the JSON value")
    }
    return value
}

private class JsonParser(private val src: String) {
    private var pos = 0

    fun atEnd(): Boolean = pos >= src.length

    fun skipWhitespace() {
        while (pos < src.length && src[pos].isWhitespace()) pos++
    }

    fun parseValue(): JsonValue {
        skipWhitespace()
        if (atEnd()) throw Protocol.MalformedResponse("unexpected end of input")
        return when (src[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonValue.Str(parseString())
            't' -> parseLiteral("true", JsonValue.Bool(true))
            'f' -> parseLiteral("false", JsonValue.Bool(false))
            'n' -> parseLiteral("null", JsonValue.Null)
            else -> parseNumber()
        }
    }

    private fun parseLiteral(literal: String, value: JsonValue): JsonValue {
        if (!src.startsWith(literal, pos)) {
            throw Protocol.MalformedResponse("expected '$literal'")
        }
        pos += literal.length
        return value
    }

    private fun parseObject(): JsonValue {
        pos++ // {
        val fields = LinkedHashMap<String, JsonValue>()
        skipWhitespace()
        if (pos < src.length && src[pos] == '}') { pos++; return JsonValue.Obj(fields) }
        while (true) {
            skipWhitespace()
            if (pos >= src.length || src[pos] != '"') {
                throw Protocol.MalformedResponse("expected a field name")
            }
            val key = parseString()
            skipWhitespace()
            if (pos >= src.length || src[pos] != ':') {
                throw Protocol.MalformedResponse("expected ':'")
            }
            pos++
            fields[key] = parseValue()
            skipWhitespace()
            if (pos >= src.length) throw Protocol.MalformedResponse("unterminated object")
            when (src[pos]) {
                ',' -> pos++
                '}' -> { pos++; return JsonValue.Obj(fields) }
                else -> throw Protocol.MalformedResponse("expected ',' or '}'")
            }
        }
    }

    private fun parseArray(): JsonValue {
        pos++ // [
        val items = ArrayList<JsonValue>()
        skipWhitespace()
        if (pos < src.length && src[pos] == ']') { pos++; return JsonValue.Arr(items) }
        while (true) {
            items += parseValue()
            skipWhitespace()
            if (pos >= src.length) throw Protocol.MalformedResponse("unterminated array")
            when (src[pos]) {
                ',' -> pos++
                ']' -> { pos++; return JsonValue.Arr(items) }
                else -> throw Protocol.MalformedResponse("expected ',' or ']'")
            }
        }
    }

    private fun parseString(): String {
        pos++ // opening quote
        val sb = StringBuilder()
        while (true) {
            if (pos >= src.length) throw Protocol.MalformedResponse("unterminated string")
            when (val c = src[pos]) {
                '"' -> { pos++; return sb.toString() }
                '\\' -> {
                    pos++
                    if (pos >= src.length) throw Protocol.MalformedResponse("dangling escape")
                    when (val e = src[pos]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 >= src.length) {
                                throw Protocol.MalformedResponse("truncated unicode escape")
                            }
                            val hex = src.substring(pos + 1, pos + 5)
                            val code = hex.toIntOrNull(16)
                                ?: throw Protocol.MalformedResponse("bad unicode escape")
                            sb.append(code.toChar())
                            pos += 4
                        }
                        else -> throw Protocol.MalformedResponse("unknown escape '\\$e'")
                    }
                    pos++
                }
                else -> { sb.append(c); pos++ }
            }
        }
    }

    private fun parseNumber(): JsonValue {
        val start = pos
        if (pos < src.length && (src[pos] == '-' || src[pos] == '+')) pos++
        var sawDigit = false
        while (pos < src.length && src[pos].isDigit()) { pos++; sawDigit = true }
        if (pos < src.length && src[pos] == '.') {
            pos++
            while (pos < src.length && src[pos].isDigit()) { pos++; sawDigit = true }
        }
        if (!sawDigit) throw Protocol.MalformedResponse("expected a number")
        if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
            pos++
            if (pos < src.length && (src[pos] == '-' || src[pos] == '+')) pos++
            while (pos < src.length && src[pos].isDigit()) pos++
        }
        val raw = src.substring(start, pos)
        val value = raw.toDoubleOrNull()
            ?: throw Protocol.MalformedResponse("bad number '$raw'")
        return JsonValue.Num(value, raw)
    }
}
