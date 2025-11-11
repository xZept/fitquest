package com.example.fitquest.utils

internal fun String.parseCsvLine(): List<String> {
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < length) {
        val c = this[i]
        when (c) {
            '"' -> {
                if (inQuotes && i + 1 < length && this[i + 1] == '"') {
                    sb.append('"'); i++ // escaped quote
                } else {
                    inQuotes = !inQuotes
                }
            }
            ',' -> if (!inQuotes) {
                out.add(sb.toString())
                sb.setLength(0)
            } else {
                sb.append(c)
            }
            else -> sb.append(c)
        }
        i++
    }
    out.add(sb.toString())
    return out
}
