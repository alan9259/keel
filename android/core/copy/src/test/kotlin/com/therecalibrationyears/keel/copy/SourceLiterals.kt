package com.therecalibrationyears.keel.copy

/**
 * A string literal found in source code: its text with escapes resolved and every
 * interpolation (`\(x)` in Swift, `$x` / `${x}` in Kotlin) replaced by [PLACEHOLDER], so
 * the same sentence compares equal on both platforms.
 */
data class Literal(val text: String, val line: Int, val raw: Boolean = false)

const val PLACEHOLDER = "{}"

/**
 * Extracts string literals from Swift source, skipping comments, `#if DEBUG` branches and
 * `#Preview` blocks (debug-only text never ships, so it is not reference copy).
 */
object SwiftLiterals {
    fun extract(source: String): List<Literal> = Scanner(stripDebugBranches(source)).run()

    /** Blanks the lines of every `#if DEBUG` branch (keeping `#else` branches and line numbers). */
    fun stripDebugBranches(source: String): String {
        val skipping = ArrayDeque<Pair<Boolean, Boolean>>() // (isDebugCondition, inDebugBranch)
        return source.lines().joinToString("\n") { line ->
            val t = line.trim()
            val wasSkipping = skipping.any { it.second }
            when {
                t.startsWith("#if ") -> {
                    val cond = t.removePrefix("#if ").trim()
                    val debug = cond == "DEBUG"
                    val notDebug = cond == "!DEBUG"
                    skipping.addLast((debug || notDebug) to debug)
                    ""
                }
                t.startsWith("#else") || t.startsWith("#elseif") -> {
                    val (isDebug, inDebug) = skipping.removeLast()
                    // #else of `#if DEBUG` is release code; #else of `#if !DEBUG` is debug code.
                    skipping.addLast(isDebug to (isDebug && !inDebug))
                    ""
                }
                t.startsWith("#endif") -> { skipping.removeLastOrNull(); "" }
                wasSkipping -> ""
                else -> line
            }
        }
    }

    private class Scanner(val src: String) {
        val out = mutableListOf<Literal>()
        var i = 0
        var line = 1

        // Literals inside a #Preview are sample text: skip from `#Preview` to its closing brace.
        var braceDepth = 0
        var previewDepth = -1
        var pendingPreview = false
        val inPreview get() = pendingPreview || previewDepth >= 0

        fun run(): List<Literal> {
            scanCode(stopAtCloseParen = false)
            return out
        }

        fun at(o: Int = 0): Char = if (i + o < src.length) src[i + o] else '\u0000'

        fun advance(n: Int = 1) {
            repeat(n) { if (i < src.length) { if (src[i] == '\n') line++; i++ } }
        }

        /** Scans code; inside an interpolation, stops after the `)` that closes it. */
        fun scanCode(stopAtCloseParen: Boolean) {
            var parens = 0
            while (i < src.length) {
                val c = at()
                when {
                    c == '/' && at(1) == '/' -> while (i < src.length && at() != '\n') advance()
                    c == '/' && at(1) == '*' -> skipBlockComment()
                    src.startsWith("#Preview", i) -> { pendingPreview = true; advance(8) }
                    c == '#' && rawStringHashes() > 0 -> stringLiteral(rawStringHashes())
                    c == '"' -> stringLiteral(0)
                    c == '{' -> {
                        braceDepth++
                        if (pendingPreview) { previewDepth = braceDepth; pendingPreview = false }
                        advance()
                    }
                    c == '}' -> {
                        if (braceDepth == previewDepth) previewDepth = -1
                        braceDepth--
                        advance()
                    }
                    c == '(' -> { parens++; advance() }
                    c == ')' -> {
                        advance()
                        if (stopAtCloseParen && parens == 0) return
                        parens--
                    }
                    else -> advance()
                }
            }
        }

        fun skipBlockComment() {
            var depth = 0
            while (i < src.length) {
                when {
                    at() == '/' && at(1) == '*' -> { depth++; advance(2) }
                    at() == '*' && at(1) == '/' -> { depth--; advance(2); if (depth == 0) return }
                    else -> advance()
                }
            }
        }

        /** Number of `#` before a `"` at the cursor (a raw string), else 0. */
        fun rawStringHashes(): Int {
            var n = 0
            while (at(n) == '#') n++
            return if (n > 0 && at(n) == '"') n else 0
        }

        fun stringLiteral(hashes: Int) {
            val startLine = line
            advance(hashes)
            val multiline = src.startsWith("\"\"\"", i)
            advance(if (multiline) 3 else 1)
            val close = (if (multiline) "\"\"\"" else "\"") + "#".repeat(hashes)
            val escape = "\\" + "#".repeat(hashes)
            val body = StringBuilder()
            while (i < src.length) {
                if (src.startsWith(close, i)) { advance(close.length); break }
                if (src.startsWith(escape, i)) {
                    advance(escape.length)
                    when (val e = at()) {
                        '(' -> { advance(); scanCode(stopAtCloseParen = true); body.append(PLACEHOLDER) }
                        'n' -> { body.append('\n'); advance() }
                        't' -> { body.append('\t'); advance() }
                        'r' -> { body.append('\r'); advance() }
                        '0' -> { body.append('\u0000'); advance() }
                        'u' -> {
                            val end = src.indexOf('}', i)
                            body.appendCodePoint(src.substring(i + 2, end).toInt(16))
                            advance(end + 1 - i)
                        }
                        // Multi-line continuation: keep the newline so dedenting still sees the next line.
                        '\n' -> { advance(); body.append(CONTINUATION).append('\n') }
                        else -> { body.append(e); advance() }
                    }
                    continue
                }
                body.append(at())
                advance()
            }
            if (inPreview) return
            val text = if (multiline) dedentMultiline(body.toString()) else body.toString()
            out += Literal(text, startLine, raw = hashes > 0)
        }

        /** Swift multi-line strings drop the opening newline and the closing line's indentation. */
        fun dedentMultiline(body: String): String {
            val lines = body.removePrefix("\n").split("\n")
            val indent = lines.last().takeWhile { it == ' ' || it == '\t' }
            return lines.dropLast(1).joinToString("\n") { it.removePrefix(indent) }
                .replace(CONTINUATION + "\n", "")
        }
    }

    private const val CONTINUATION = "\u0001"
}

/**
 * Extracts string literals from Kotlin source, skipping comments and character literals.
 * Raw (`"""`) strings are returned with [Literal.raw] set: they hold SQL and similar, and
 * copy must not be written as a raw string.
 */
object KotlinLiterals {
    fun extract(source: String): List<Literal> = Scanner(source).run()

    private class Scanner(val src: String) {
        val out = mutableListOf<Literal>()
        var i = 0
        var line = 1

        fun run(): List<Literal> {
            scanCode(stopAtCloseBrace = false)
            return out
        }

        fun at(o: Int = 0): Char = if (i + o < src.length) src[i + o] else '\u0000'

        fun advance(n: Int = 1) {
            repeat(n) { if (i < src.length) { if (src[i] == '\n') line++; i++ } }
        }

        /** Scans code; inside a `${...}` template, stops after the `}` that closes it. */
        fun scanCode(stopAtCloseBrace: Boolean) {
            var braces = 0
            while (i < src.length) {
                val c = at()
                when {
                    c == '/' && at(1) == '/' -> while (i < src.length && at() != '\n') advance()
                    c == '/' && at(1) == '*' -> skipBlockComment()
                    c == '\'' -> charLiteral()
                    c == '"' -> stringLiteral()
                    c == '{' -> { braces++; advance() }
                    c == '}' -> {
                        advance()
                        if (stopAtCloseBrace && braces == 0) return
                        braces--
                    }
                    else -> advance()
                }
            }
        }

        fun skipBlockComment() {
            var depth = 0
            while (i < src.length) {
                when {
                    at() == '/' && at(1) == '*' -> { depth++; advance(2) }
                    at() == '*' && at(1) == '/' -> { depth--; advance(2); if (depth == 0) return }
                    else -> advance()
                }
            }
        }

        fun charLiteral() {
            advance()
            if (at() == '\\') advance(2) else advance()
            while (i < src.length && at() != '\'') advance() // \uXXXX
            advance()
        }

        fun stringLiteral() {
            val startLine = line
            val raw = src.startsWith("\"\"\"", i)
            advance(if (raw) 3 else 1)
            val body = StringBuilder()
            while (i < src.length) {
                if (raw && src.startsWith("\"\"\"", i)) {
                    while (at(3) == '"') { body.append('"'); advance() } // """" ends with a quote
                    advance(3); break
                }
                if (!raw && at() == '"') { advance(); break }
                if (!raw && at() == '\\') {
                    advance()
                    when (val e = at()) {
                        'n' -> body.append('\n')
                        't' -> body.append('\t')
                        'r' -> body.append('\r')
                        'b' -> body.append('\b')
                        'u' -> { body.append(src.substring(i + 1, i + 5).toInt(16).toChar()); advance(4) }
                        else -> body.append(e) // \" \' \\ \$
                    }
                    advance()
                    continue
                }
                if (at() == '$' && at(1) == '{') {
                    advance(2); scanCode(stopAtCloseBrace = true); body.append(PLACEHOLDER)
                    continue
                }
                if (at() == '$' && (at(1).isLetter() || at(1) == '_')) {
                    advance()
                    while (at().isLetterOrDigit() || at() == '_') advance()
                    body.append(PLACEHOLDER)
                    continue
                }
                body.append(at())
                advance()
            }
            out += Literal(body.toString(), startLine, raw)
        }
    }
}
