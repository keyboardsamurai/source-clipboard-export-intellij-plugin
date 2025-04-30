plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = "com.keyboardsamurais.intellij.plugin"
version = "1.7"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.knuddels:jtokkit:1.1.0")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2") // For parameterized tests
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.9.24")
    testImplementation("io.mockk:mockk:1.13.10")
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2023.2.6")
    type.set("IC")
    plugins.set(listOf("java"))
    updateSinceUntilBuild.set(false)
}
tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    // Configure test task to use JUnit 5
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }

        // Increase heap size to avoid OutOfMemoryError
        jvmArgs("-Xmx2g")

        // Point to our custom logging.properties file
        systemProperty("java.util.logging.config.file", "${project.projectDir}/src/test/resources/logging.properties")
    }

    // Task to run all tests
    register("runAllTests") {
        description = "Runs all tests in the project"
        group = "verification"
        dependsOn(test)

        doLast {
            println("All tests have been executed.")
        }
    }
    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("252.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
