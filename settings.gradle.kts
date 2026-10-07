pluginManagement {
    repositories {
        // No content filters here on purpose: a "com.google.*" filter would pin the KSP plugin
        // (com.google.devtools.ksp) to google(), where it is not published.
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

rootProject.name = "LRRViewer"
include(":app")
