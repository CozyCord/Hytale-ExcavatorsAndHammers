plugins {
    java
}

group = "net.cozystudios"
version = "1.0.0"

// Path to your Hytale installation
val hytaleServerJar = "C:/Users/dingd/AppData/Roaming/Hytale/install/release/package/game/latest/Server/HytaleServer.jar"

repositories {
    mavenCentral()
}

dependencies {
    // HytaleServer.jar from local Hytale installation (includes guava, gson, etc.)
    compileOnly(files(hytaleServerJar))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.AZUL)
    }
}

tasks {
    withType<JavaCompile> {
        options.release.set(25)
    }

    jar {
        archiveBaseName.set("ExcavatorsAndHammers")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("")
    }

    processResources {
        filesMatching("manifest.json") {
            expand(
                "version" to project.version
            )
        }
    }
}
