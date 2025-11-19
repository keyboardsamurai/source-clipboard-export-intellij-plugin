plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.10.4"
    id("jacoco")
}

group = "com.keyboardsamurais.intellij.plugin"
version = "2.1"

val jacocoExtension = extensions.getByType(JacocoPluginExtension::class.java).apply {
    toolVersion = "0.8.12"
}

val sourceSets = extensions.getByType(SourceSetContainer::class.java)
val mainSourceSet = sourceSets.named("main").get()
val testSourceSet = sourceSets.named("test").get()

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
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("Git4Idea")

        pluginVerifier()
        zipSigner()
    }

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2") // For parameterized tests
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.9.24")
    // Mockito 5.3.0+ includes inline mocking by default in mockito-core
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

    fun Test.applyCommonTestConfig() {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        jvmArgs("-Xmx2g", "-XX:+EnableDynamicAgentLoading")
        systemProperty(
            "java.util.logging.config.file",
            "${project.projectDir}/src/test/resources/logging.properties"
        )
        addTestListener(object : TestListener {
            override fun beforeSuite(desc: TestDescriptor) {}
            override fun beforeTest(desc: TestDescriptor) {}
            override fun afterTest(desc: TestDescriptor, result: TestResult) {}
            override fun afterSuite(desc: TestDescriptor, result: TestResult) {
                if (desc.parent == null) {
                    logger.lifecycle(
                        "Test summary: ${result.resultType} - ${result.testCount} tests, ${result.successfulTestCount} succeeded, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped"
                    )
                }
            }
        })
    }

    // Configure IntelliJ harness tests
    val intellijHarnessTest = named<Test>("test") {
        applyCommonTestConfig()
    }

    // Ensure Jacoco agent is attached to every Gradle Test task (the IntelliJ harness runs in a separate JVM).
    withType<Test>().configureEach {
        if (extensions.findByName("jacoco") == null) {
            jacocoExtension.applyTo(this)
        }
        extensions.configure(JacocoTaskExtension::class.java) {
            isEnabled = true
            setDestinationFile(layout.buildDirectory.file("jacoco/${name}.exec").get().asFile)
            isIncludeNoLocationClasses = true
            excludes = listOf("jdk.internal.*")
        }
    }

    withType<JacocoReport>().configureEach {
        reports {
            html.required.set(true)
            xml.required.set(true)
            csv.required.set(false)
        }
    }

    named<JacocoReport>("jacocoTestReport") {
        dependsOn(intellijHarnessTest)
        executionData.setFrom(layout.buildDirectory.file("jacoco/test.exec").get().asFile)
        classDirectories.setFrom(files(mainSourceSet.output))
        sourceDirectories.setFrom(files(mainSourceSet.allSource.srcDirs))
    }

    // Task to run all tests
    register("runAllTests") {
        description = "Runs all tests in the project"
        group = "verification"
        dependsOn(intellijHarnessTest)

        doLast {
            println("All tests have been executed.")
        }
    }

    // Create a task to set up disabled plugins before running IDE
    val setupDisabledPlugins by registering {
        notCompatibleWithConfigurationCache("Creates files at execution time")
        
        doLast {
            val sandboxDir = layout.buildDirectory.dir("idea-sandbox/IC-2024.2.4/config").get().asFile
            sandboxDir.mkdirs()
            val disabledPluginsFile = sandboxDir.resolve("disabled_plugins.txt")
            disabledPluginsFile.writeText("com.intellij.gradle\n")
            logger.lifecycle("Created disabled_plugins.txt at: ${disabledPluginsFile.absolutePath}")
        }
    }

    // For sandbox runs, disable the Gradle plugin to avoid startup exceptions in 2024.2.x
    // caused by Gradle JVM support matrix parsing newer Java versions.
    runIde {
        // Ensure disabled plugins are set up before running
        dependsOn(setupDisabledPlugins)
        
        // Disable configuration cache for this task
        notCompatibleWithConfigurationCache("Depends on file creation task")
        
        // Increase memory
        jvmArgs = listOf("-Xmx2048m")
    }
}
