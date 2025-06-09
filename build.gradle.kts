plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.6.0"
}

group = "com.keyboardsamurais.intellij.plugin"
version = "1.7"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.knuddels:jtokkit:1.1.0")
    runtimeOnly("com.knuddels:jtokkit:1.1.0") // Ensure the library is included in the plugin distribution

    // IntelliJ Platform dependencies
    intellijPlatform {
        intellijIdeaCommunity("2024.2.4")
        bundledPlugin("com.intellij.java")

        pluginVerifier()
        zipSigner()
        instrumentationTools()
    }

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2") // For parameterized tests
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.9.24")
    testImplementation("org.mockito:mockito-core:5.17.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.17.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("io.mockk:mockk:1.13.10")
}

intellijPlatform {
    projectName = "Export Source to Clipboard"

    pluginConfiguration {
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "242"
            untilBuild = "252.*"
        }
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
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
}
