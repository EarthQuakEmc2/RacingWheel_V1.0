pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

fun voiceEnabled(): Boolean {
    val fromCmd = gradle.startParameter.projectProperties["racingwheel.voice"]
    if (fromCmd != null) return fromCmd != "false"
    val props = java.util.Properties()
    val propsFile = file("gradle.properties")
    if (propsFile.exists()) propsFile.inputStream().use { props.load(it) }
    return props.getProperty("racingwheel.voice", "true") != "false"
}

rootProject.name = "RacingWheelPro"
include(":app")
if (voiceEnabled()) include(":voice")
