package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface ReplyBlock {
    data class Heading(val text: String) : ReplyBlock
    data class Bullet(val text: String, val nested: Boolean) : ReplyBlock
    data class Numbered(val number: String, val text: String) : ReplyBlock
    data class KeyValue(val label: String, val value: String) : ReplyBlock
    data class Paragraph(val text: String) : ReplyBlock
    data object Divider : ReplyBlock
}

private val HeadingRegex = Regex("^#{1,6}\\s+(.+)$")
private val StandaloneBoldLineRegex = Regex("^\\*\\*([^*\n]{2,80})\\*\\*:?$")
private val DividerRegex = Regex("^([-*_]\\s*){3,}$")
private val NumberedRegex = Regex("^(\\d{1,2})[.)]\\s+(.+)$")
private val BulletRegex = Regex("^(\\s*)[*\\-•+]+\\s*(?:[*\\-•+]\\s*)*(.+)$")
private val LeadingJunkMarkersRegex = Regex("^(?:[*\\-•]+\\s*)+")
private val KeyValueRegex = Regex("^([A-Za-z][A-Za-z0-9 /&()'.]{1,26}):\\s+(.+)$")

@Composable
fun FormattedAssistantText(
    rawText: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(rawText) { parseReplyBlocks(rawText) }
    val bodyColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentDotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    val nestedDotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    val codeBgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    SelectionContainer(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            blocks.forEachIndexed { index, block ->
                when (block) {
                    is ReplyBlock.Heading -> {
                        if (index > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Text(
                            text = parseInlineFormatting(
                                raw = block.text,
                                strongColor = bodyColor,
                                codeBgColor = codeBgColor
                            ),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 20.sp
                            ),
                            color = bodyColor
                        )
                    }

                    is ReplyBlock.Divider -> {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = dividerColor
                        )
                    }

                    is ReplyBlock.KeyValue -> {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        fontWeight = FontWeight.SemiBold,
                                        color = bodyColor
                                    )
                                ) {
                                    append(block.label)
                                    append(": ")
                                }
                                append(
                                    parseInlineFormatting(
                                        raw = block.value,
                                        strongColor = bodyColor,
                                        codeBgColor = codeBgColor
                                    )
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                            color = bodyColor.copy(alpha = 0.9f)
                        )
                    }

                    is ReplyBlock.Bullet -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = if (block.nested) 14.dp else 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(if (block.nested) 4.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(if (block.nested) nestedDotColor else accentDotColor)
                            )
                            Text(
                                text = parseInlineFormatting(
                                    raw = block.text,
                                    strongColor = bodyColor,
                                    codeBgColor = codeBgColor
                                ),
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                                color = bodyColor.copy(alpha = 0.92f),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    is ReplyBlock.Numbered -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${block.number}.",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 21.sp
                                ),
                                color = mutedColor,
                                modifier = Modifier.widthIn(min = 18.dp)
                            )
                            Text(
                                text = parseInlineFormatting(
                                    raw = block.text,
                                    strongColor = bodyColor,
                                    codeBgColor = codeBgColor
                                ),
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                                color = bodyColor.copy(alpha = 0.92f),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    is ReplyBlock.Paragraph -> {
                        Text(
                            text = parseInlineFormatting(
                                raw = block.text,
                                strongColor = bodyColor,
                                codeBgColor = codeBgColor
                            ),
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                            color = bodyColor.copy(alpha = 0.92f)
                        )
                    }
                }
            }
        }
    }
}

private fun parseReplyBlocks(rawText: String): List<ReplyBlock> {
    val lines = rawText.replace("\r\n", "\n").split("\n")
    val result = mutableListOf<ReplyBlock>()

    for (rawLine in lines) {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) continue

        if (DividerRegex.matches(trimmed)) {
            if (result.lastOrNull() !is ReplyBlock.Divider) {
                result.add(ReplyBlock.Divider)
            }
            continue
        }

        HeadingRegex.matchEntire(trimmed)?.let { m ->
            val clean = stripRawMarkers(m.groupValues[1])
            if (clean.isNotEmpty()) result.add(ReplyBlock.Heading(clean))
            continue
        }

        StandaloneBoldLineRegex.matchEntire(trimmed)?.let { m ->
            val clean = m.groupValues[1].trim().trimEnd(':')
            if (clean.isNotEmpty()) result.add(ReplyBlock.Heading(clean))
            continue
        }

        NumberedRegex.matchEntire(trimmed)?.let { m ->
            val num = m.groupValues[1]
            val body = cleanBulletBody(m.groupValues[2])
            if (body.isNotEmpty()) result.add(ReplyBlock.Numbered(num, body))
            continue
        }

        BulletRegex.matchEntire(rawLine)?.let { m ->
            val leadingSpaces = m.groupValues[1].length
            val body = cleanBulletBody(m.groupValues[2])
            if (body.isNotEmpty()) {
                result.add(ReplyBlock.Bullet(text = body, nested = leadingSpaces >= 2))
            }
            continue
        }

        val unboldedLeading = trimmed
            .replace(Regex("^\\*\\*([^*\n]+?)\\*\\*:\\s*"), "$1: ")
            .replace(Regex("^\\*\\*([^*\n]+?):\\*\\*\\s*"), "$1: ")

        val kvMatch = KeyValueRegex.matchEntire(unboldedLeading)
        if (kvMatch != null && !unboldedLeading.startsWith("http", ignoreCase = true)) {
            val label = stripRawMarkers(kvMatch.groupValues[1])
            val value = kvMatch.groupValues[2].trim()
            if (label.isNotEmpty() && value.isNotEmpty()) {
                result.add(ReplyBlock.KeyValue(label, value))
                continue
            }
        }

        result.add(ReplyBlock.Paragraph(trimmed))
    }

    return result
}

private fun cleanBulletBody(raw: String): String {
    // Strip accidental double markers like "* - Room" or "- * Subject"
    return raw.trim().replace(LeadingJunkMarkersRegex, "").trim()
}

private fun stripRawMarkers(raw: String): String =
    raw.replace("**", "").replace("__", "").replace("`", "").trim()

private fun parseInlineFormatting(
    raw: String,
    strongColor: Color,
    codeBgColor: Color
): AnnotatedString = buildAnnotatedString {
    // Normalize messy separators like " - " only when preceded by raw asterisk clutter,
    // and parse **bold**, __bold__, `code`, and *italic* without leaving stray asterisks.
    var i = 0
    val n = raw.length
    while (i < n) {
        // Bold: **text**
        if (i + 1 < n && raw[i] == '*' && raw[i + 1] == '*') {
            val close = raw.indexOf("**", startIndex = i + 2)
            if (close > i + 2) {
                val inner = raw.substring(i + 2, close)
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strongColor)) {
                    append(inner.replace("*", ""))
                }
                i = close + 2
                continue
            } else {
                // Unclosed trailing ** during streaming — omit raw asterisks
                i += 2
                continue
            }
        }

        // Bold: __text__
        if (i + 1 < n && raw[i] == '_' && raw[i + 1] == '_') {
            val close = raw.indexOf("__", startIndex = i + 2)
            if (close > i + 2) {
                val inner = raw.substring(i + 2, close)
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strongColor)) {
                    append(inner)
                }
                i = close + 2
                continue
            } else {
                i += 2
                continue
            }
        }

        // Inline code: `text`
        if (raw[i] == '`') {
            val close = raw.indexOf('`', startIndex = i + 1)
            if (close > i + 1) {
                val inner = raw.substring(i + 1, close)
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        background = codeBgColor,
                        color = strongColor
                    )
                ) {
                    append(" $inner ")
                }
                i = close + 1
                continue
            } else {
                i += 1
                continue
            }
        }

        // Italic: *text* (only when not followed by whitespace)
        if (raw[i] == '*') {
            if (i + 1 < n && !raw[i + 1].isWhitespace()) {
                val close = raw.indexOf('*', startIndex = i + 1)
                if (close > i + 1 && !raw[close - 1].isWhitespace()) {
                    val inner = raw.substring(i + 1, close)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(inner)
                    }
                    i = close + 1
                    continue
                }
            }
            // Stray asterisk — skip so raw '*' never clutters the reply
            i += 1
            continue
        }

        append(raw[i])
        i += 1
    }
}
