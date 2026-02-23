plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

group = "com.riftalstudios"
version = "1.0"
description = "Custom RPG mechanics plugin for PaperMC servers"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.codemc.io/repository/maven-releases/") // PacketEvents
    maven("https://repo.codemc.io/repository/maven-snapshots/") // PacketEvents snapshots
}

dependencies {
    // Paper dev bundle (provides NMS access)
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")

    // PacketEvents - client-side packet manipulation
    compileOnly("com.github.retrooper:packetevents-spigot:2.11.2")

    // Database (downloaded at runtime via plugin.yml libraries)
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly("com.h2database:h2:2.2.224")

    // Test dependencies (using Paper 1.21 for MockBukkit compatibility)
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.21:3.107.0")
    testImplementation("org.mockito:mockito-core:5.8.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("${project.name}-${project.version}.jar")
    }

    test {
        useJUnitPlatform()
    }

    runServer {
        minecraftVersion("1.21.11")
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
