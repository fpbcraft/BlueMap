logger.lifecycle("""
## Building BlueMap ...
Java: ${System.getProperty("java.version")}
JVM: ${System.getProperty("java.vm.version")} (${System.getProperty("java.vendor")})
Arch: ${System.getProperty("os.arch")} 
""")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven ("https://maven.minecraftforge.net" )
        maven ("https://maven.fabricmc.net/" )
        maven ("https://maven.neoforged.net/releases" )
    }
}

rootProject.name = "bluemap"

includeBuild("api")

include(":core")
include(":common")

// CI can configure only its selected platform; local/upstream builds retain all targets.
val allPlatforms = setOf("cli", "fabric", "forge", "neoforge", "paper", "spigot", "sponge")
val platforms = providers.gradleProperty("bluemapPlatforms").orNull
    ?.split(",")?.map { it.trim() }?.toSet() ?: allPlatforms
require(platforms.isNotEmpty() && platforms.all { it in allPlatforms }) {
    "bluemapPlatforms must contain comma-separated supported platforms: $allPlatforms"
}
platforms.forEach { implementation(it) }

fun implementation(name: String) {
    val project = ":$name"
    include(project)
    project(project).projectDir = file("implementations/$name")
}
