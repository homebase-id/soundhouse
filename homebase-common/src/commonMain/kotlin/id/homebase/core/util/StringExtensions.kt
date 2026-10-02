@file:Suppress("unused")

package id.homebase.core.util

import id.homebase.api.util.truncateToCodePoints

fun String.initials(): String {
    val tokens =
        this
            .trim()
            .split("\\s+".toRegex())
            .filter { it.isNotEmpty() }

    return when {
        tokens.size >= 2 ->
            "${tokens.first().truncateToCodePoints(1)}${tokens.last().truncateToCodePoints(1)}"
                .uppercase()
                .trim()

        tokens.size == 1 ->
            tokens.first().truncateToCodePoints(1).uppercase().trim()

        else -> ""
    }
}