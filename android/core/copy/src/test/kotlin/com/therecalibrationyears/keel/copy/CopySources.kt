package com.therecalibrationyears.keel.copy

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A literal with where it came from, for failure messages and the `// not-copy:` marker. */
data class SourcedLiteral(val file: String, val literal: Literal, val sourceLine: String) {
    val text get() = literal.text
    override fun toString() = "$file:${literal.line}  \"${text.replace("\n", "\\n")}\""
}

/** An Android wording that differs from iOS (§3.8 of the port plan), with the owner's sign-off. */
data class Substitution(val ios: String, val android: String, val approvedByOwner: Boolean, val note: String)

/** New Android wording with no iOS original (e.g. a TalkBack label iOS lacks), for sign-off. */
data class Addition(val android: String, val approvedByOwner: Boolean, val note: String)

/** Reads the iOS reference sources and the shipping Android sources from this repo. */
object CopySources {
    val repoRoot = File(System.getProperty("keel.repoRoot") ?: error("keel.repoRoot not set (run via Gradle)"))
    private val iosRoot = File(repoRoot, "Keel")
    private val androidRoot = File(repoRoot, "android")

    /** iOS files that only exist for debugging; nothing in them ships. */
    private val iosDebugOnly = setOf("App/DebugHarness.swift", "App/CloudKitDebugProbe.swift")

    /** Marks a shipping Kotlin literal that is not user-facing (a log tag, an exception message). */
    const val NOT_COPY_MARKER = "// not-copy:"

    val iosLiterals: List<SourcedLiteral> by lazy {
        iosRoot.walkTopDown()
            .filter { it.isFile && it.extension == "swift" }
            .filter { it.relativeTo(iosRoot).invariantSeparatorsPath !in iosDebugOnly }
            .flatMap { sourced(it, SwiftLiterals.extract(it.readText()), it.relativeTo(repoRoot)) }
            .toList()
    }

    /** Every literal in `src/main` and `src/release` Kotlin (debug source sets never ship). */
    val androidShippingLiterals: List<SourcedLiteral> by lazy {
        androidRoot.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".gradle" }
            .filter { it.isFile && it.extension == "kt" }
            .filter { f -> f.relativeTo(androidRoot).invariantSeparatorsPath.let { "/src/main/" in it || "/src/release/" in it } }
            .flatMap { sourced(it, KotlinLiterals.extract(it.readText()), it.relativeTo(repoRoot)) }
            .toList()
    }

    val substitutions: List<Substitution> by lazy {
        val json = resource("platform-substitutions.json")
        Json.parseToJsonElement(json).jsonArray.map {
            val o = it.jsonObject
            Substitution(
                ios = o.getValue("ios").jsonPrimitive.content,
                android = o.getValue("android").jsonPrimitive.content,
                approvedByOwner = o.getValue("approvedByOwner").jsonPrimitive.boolean,
                note = o["note"]?.jsonPrimitive?.content.orEmpty(),
            )
        }
    }

    val additions: List<Addition> by lazy {
        Json.parseToJsonElement(resource("android-additions.json")).jsonArray.map {
            val o = it.jsonObject
            Addition(
                android = o.getValue("android").jsonPrimitive.content,
                approvedByOwner = o.getValue("approvedByOwner").jsonPrimitive.boolean,
                note = o["note"]?.jsonPrimitive?.content.orEmpty(),
            )
        }
    }

    /** Lines of a list resource; `#` starts a comment line and `\n` is a newline. */
    fun listResource(name: String): Set<String> = resource(name).lines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.replace("\\n", "\n") }
        .toSet()

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader.getResource(name)) { "missing test resource $name" }.readText()

    private fun sourced(file: File, literals: List<Literal>, path: File): Sequence<SourcedLiteral> {
        val lines = file.readLines()
        return literals.asSequence().map {
            SourcedLiteral(path.invariantSeparatorsPath, it, lines.getOrElse(it.line - 1) { "" })
        }
    }
}

/**
 * Whether a literal reads as user-facing text rather than a key, id, format or path:
 * it has a space or starts with a capitalised word. Lower-case single words ("earthy",
 * "keel.colourMode") are identifiers, shared with iOS by design.
 */
fun looksLikeCopy(text: String): Boolean {
    val t = text.replace(PLACEHOLDER, "").trim()
    if (t.none { it.isLetter() }) return false
    if (PATH_DATA.matches(t) || SQL.containsMatchIn(t)) return false
    return ' ' in t || CAPITALISED.containsMatchIn(t)
}

private val PATH_DATA = Regex("""^[MmZzLlHhVvCcSsQqTtAa0-9.,\s-]+$""")
/** Room queries, including the clause lines of a query split across `+` (upper case only). */
private val SQL = Regex("""^(SELECT|INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|PRAGMA|WITH|FROM|WHERE|AND|OR|ORDER BY|GROUP BY|JOIN|LEFT JOIN|LIMIT)\b""")
private val CAPITALISED = Regex("^[A-Z][a-z]")
