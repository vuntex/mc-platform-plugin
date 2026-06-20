plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.mcplatform"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper API – provided by the server at runtime, so compileOnly.
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")

    // Shared, dependency-free contract published to mavenLocal by the backend (Variante B).
    // Must be shaded into the jar: mavenLocal artifacts aren't reachable by Paper's library loader.
    implementation("com.mcplatform:plugin-protocol:0.1.0-SNAPSHOT")

    // Declared now, wired in later prompts (4 = JSON mapping, 5 = Redis transport). Bundled+relocated.
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("io.lettuce:lettuce-core:6.5.1.RELEASE")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Live EventBus proof against a real Redis (like the backend's infra-cache tests).
    // Docker-gated at runtime (assumeTrue) so the build stays green where Docker is absent.
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    // Keep the plugin descriptor version in sync with the build.
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveBaseName.set("mc-platform-plugin")
    // Replace the thin jar so the server loads the fat jar that bundles plugin-protocol et al.
    archiveClassifier.set("")
    // Relocate third-party libs so they never clash with other plugins' (or Paper's) copies.
    relocate("com.google.gson", "com.mcplatform.plugin.libs.gson")
    relocate("io.lettuce", "com.mcplatform.plugin.libs.lettuce")
    relocate("io.netty", "com.mcplatform.plugin.libs.netty")
    relocate("reactor", "com.mcplatform.plugin.libs.reactor")
    relocate("org.reactivestreams", "com.mcplatform.plugin.libs.reactivestreams")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
