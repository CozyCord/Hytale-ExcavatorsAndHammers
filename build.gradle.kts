plugins {
    java
    id("co.uzzu.dotenv.gradle") version "4.0.0"  // Use latest version  
}

group = "net.cozystudios"
version = "1.0.0"

// Path to your Hytale installation
val hytaleServerJar = env.HYTALE_SERVER_JAR

repositories {
    mavenCentral()
}

dependencies {
    // HytaleServer.jar from local Hytale installation (includes guava, gson, etc.)
    implementation (files(hytaleServerJar.value))
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
        archiveBaseName.set("MiningHammer")
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
