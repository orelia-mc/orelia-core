plugins {
    id("java")
    id("com.gradleup.shadow") version "9.5.1"
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
