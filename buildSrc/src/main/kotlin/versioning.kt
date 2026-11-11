import org.gradle.api.Project
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

fun Project.gitHash(): String {
    return runCommand("git rev-parse --verify HEAD", "-")
}

fun Project.gitClean(): Boolean {
    if (runCommand("git update-index --refresh", "NOT-CLEAN").equals("NOT-CLEAN")) return false;
    return runCommand("git diff-index HEAD --", "NOT-CLEAN").isEmpty();
}

fun Project.gitVersion(): String {
    providers.environmentVariable("RELEASE_VERSION").orNull?.let { releaseVersion ->
        require(Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?""").matches(releaseVersion)) {
            "RELEASE_VERSION must be a semantic version"
        }
        require(releaseVersion.startsWith("5.7.")) {
            "RELEASE_VERSION must stay on 5.7.x for addon compatibility"
        }
        require(!releaseVersion.startsWith("5.7.0-")) {
            "Prereleases must use patch 1 or higher for addon compatibility"
        }
        logger.lifecycle("${project.name} version: $releaseVersion")
        return releaseVersion
    }

    val lastTag = compatibleTag()
    val lastVersion = if (lastTag == "v5.7") "5.7.0" else lastTag.substring(1) // remove the leading 'v'
    val commits = runCommand("git rev-list --count $lastTag..HEAD", "0")
    val branch = runCommand("git branch --show-current", "master")
    val gitVersion = lastVersion +
            (if (branch == "master" || branch.isEmpty()) "" else "-${branch.replace('/', '.')}") +
            (if (commits == "0") "" else "-$commits") +
            (if (gitClean()) "" else "-dirty")

    logger.lifecycle("${project.name} version: $gitVersion")

    return gitVersion
}

fun Project.gitIsRelease(): Boolean {
    val lastTag = compatibleTag()
    val commits = runCommand("git rev-list --count $lastTag..HEAD", "0")
    return commits == "0" && gitClean()
}

fun Project.releaseNotes(): String {
    val file = rootProject.projectDir.resolve("release.md")
    if (!file.exists()) return ""

    return file
        .readText()
        .replace("{version}", project.version.toString())
}

private fun Project.compatibleTag(): String =
    runCommand("git tag --merged HEAD --list v5.7 v5.7.* --sort=-version:refname", "")
        .lineSequence()
        .firstOrNull { Regex("""v5\.7(?:\.\d+)?""").matches(it) }
        ?: error("No reachable stable BlueMap 5.7 tag found")

private fun Project.runCommand(cmd: String, fallback: String? = null): String {
    ProcessBuilder(cmd.split("\\s(?=(?:[^'\"`]*(['\"`])[^'\"`]*\\1)*[^'\"`]*$)".toRegex()))
        .directory(projectDir)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
        .apply {
            if (!waitFor(10, TimeUnit.SECONDS))
                throw TimeoutException("Failed to execute command: '$cmd'")
        }
        .run {
            val exitCode = waitFor()
            if (exitCode == 0) return inputStream.bufferedReader().readText().trim()

            val error = errorStream.bufferedReader().readText().trim()
            logger.warn("Failed to execute command '$cmd': $error")
            if (fallback != null) return fallback
            throw IOException(error)
        }
}
