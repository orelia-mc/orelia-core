pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            // jitpack's build environment can't always reach plugins.gradle.org (Gradle Plugin
            // Portal); the shadow plugin's actual artifact is also published to Maven Central,
            // so resolve it from there directly instead of via the Plugin Portal marker.
            if (requested.id.id == "com.gradleup.shadow") {
                useModule("com.gradleup.shadow:shadow-gradle-plugin:${requested.version}")
            }
        }
    }
}

rootProject.name = "orelia-core"
