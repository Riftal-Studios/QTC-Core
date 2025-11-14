plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "com.riftalstudios"
version = "1.0"
description = "Custom RPG mechanics plugin for PaperMC servers"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.3-R0.1-SNAPSHOT")

    // Database
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.45.0.0")

    // Test dependencies
    testImplementation("io.papermc.paper:paper-api:1.21.3-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.20:3.93.2")
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

        relocate("com.zaxxer.hikari", "com.riftalstudios.qtccore.libs.hikari")
        relocate("org.sqlite", "com.riftalstudios.qtccore.libs.sqlite")
    }

    test {
        useJUnitPlatform()
    }

    runServer {
        minecraftVersion("1.21.3")

        downloadPlugins {
            // Add any plugin dependencies here for testing
        }
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
