package com.cheradip.packbuilder

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        return
    }

    val projectRoot = detectProjectRoot()
    val builder = PackBuilder(projectRoot)

    when (args[0]) {
        "build" -> {
            val lang = argValue(args, "--lang") ?: error("--lang required")
            val version = argValue(args, "--version") ?: "1.0.0"
            builder.buildLanguage(lang, version)
        }
        "build-tier1" -> {
            val version = argValue(args, "--version") ?: "1.0.0"
            val paths = builder.buildTier1(version)
            println("Built ${paths.size} tier-1 packs")
        }
        "build-all" -> {
            val version = argValue(args, "--version") ?: "1.0.0"
            val paths = builder.buildAll(version)
            builder.syncToAiltApi(version)
            println("Built ${paths.size} language packs")
        }
        "validate" -> {
            val zipArg = argValue(args, "--zip") ?: error("--zip required")
            val zipPath = resolvePath(projectRoot, zipArg)
            val result = PackValidator.validateZip(zipPath)
            if (result.ok) {
                println("OK: $zipPath")
            } else {
                result.messages.forEach { println("ERROR: $it") }
                kotlin.system.exitProcess(1)
            }
        }
        "sync-ailt" -> {
            val version = argValue(args, "--version") ?: "1.0.0"
            builder.syncToAiltApi(version)
        }
        else -> {
            printUsage()
            kotlin.system.exitProcess(1)
        }
    }
}

private fun detectProjectRoot(): Path {
    var dir = Paths.get("").toAbsolutePath().normalize()
    repeat(6) {
        if (dir.resolve("catalog/world_languages.json").toFile().isFile) {
            return dir
        }
        dir = dir.parent ?: return Paths.get("").toAbsolutePath()
    }
    return Paths.get("").toAbsolutePath()
}

private fun resolvePath(root: Path, arg: String): Path {
    val p = Path(arg)
    return if (p.isAbsolute) p else root.resolve(arg)
}

private fun argValue(args: Array<String>, flag: String): String? {
    val idx = args.indexOf(flag)
    return if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
}

private fun printUsage() {
    println(
        """
        AI Language Tutor — pack-builder

        Commands:
          build --lang <code> --version <semver>
          build-tier1 [--catalog path] --version <semver>
          build-all --version <semver>
          validate --zip <path>
          sync-ailt --version <semver>
        """.trimIndent(),
    )
}
