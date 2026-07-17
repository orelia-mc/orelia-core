plugins {
    id("java")
    id("com.gradleup.shadow") version "9.5.1"
    id("maven-publish")
}

group = "rpg"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // VaultAPI's POM pulls in an old org.bukkit:bukkit:1.13.1 as a transitive dependency,
    // which conflicts with the org.bukkit:bukkit capability paper-api provides - exclude it.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("com.mysql:mysql-connector-j:9.1.0")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("orelia-core")
        relocate("org.sqlite", "rpg.database.libs.sqlite")
        relocate("com.mysql", "rpg.database.libs.mysql")
        relocate("com.google.protobuf", "rpg.database.libs.protobuf")
    }
    build {
        dependsOn(shadowJar)
    }
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.add("-Xlint:deprecation")
    }
    test {
        useJUnitPlatform()
    }
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// Publishes to mavenLocal under the same coordinates jitpack normally resolves
// (com.github.orelia-mc:orelia-core:main-SNAPSHOT), so orelia-world/orelia-extra can pick up
// local changes during development without waiting on a push. Temporary dev-loop aid only -
// production builds still resolve this dependency from jitpack.io.
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.github.orelia-mc"
            artifactId = "orelia-core"
            version = "main-SNAPSHOT"
            from(components["java"])
        }
    }
}
