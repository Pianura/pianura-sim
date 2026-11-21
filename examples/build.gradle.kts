// Example scenarios. Not part of the published library.
plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":algorithms"))
    // Modern look for the window. Optional: Pianura (core) does not depend on it and falls
    // back to Swing's standard look without it. Choose with -Dpianura.laf=flat|flat-dark|metal|system
    runtimeOnly("com.formdev:flatlaf:3.7.1")
}

// Every folder in the examples package is one example, started by its Main class:
//   ./gradlew run                    -> the start screen: recent scenes, favourites, all the examples
//   ./gradlew run -Pexample=radio    -> one example directly (list in examples/README.md)
val examplesDir = file("src/main/java/io/github/raffaelegrandi/pianura/examples")
val available = examplesDir.listFiles { f -> f.isDirectory }.orEmpty().map { it.name }.sorted()
val example = providers.gradleProperty("example").orNull
require(example == null || example in available) {
    "Unknown example '$example'. Available: ${available.joinToString(", ")}"
}

application {
    mainClass.set(if (example == null) "io.github.raffaelegrandi.pianura.examples.Launcher"
                  else "io.github.raffaelegrandi.pianura.examples.$example.Main")
}

// Pass -Dpianura.laf=... from the Gradle command line to the example
tasks.named<JavaExec>("run") {
    System.getProperty("pianura.laf")?.let { systemProperty("pianura.laf", it) }
}

// One runnable jar with Pianura, the algorithms, the examples and FlatLaf:
//   ./gradlew :examples:runnableJar   -> build/pianura.jar
//   java -jar build/pianura.jar             (the start screen)
//   java -jar build/pianura.jar planning    (one example directly; --list prints them)
val runnableJar by tasks.registering(Jar::class) {
    group = "distribution"
    description = "Builds build/pianura.jar, runnable with java -jar."
    archiveFileName.set("pianura.jar")
    destinationDirectory.set(rootProject.layout.buildDirectory)
    manifest {
        attributes(
            "Main-Class" to "io.github.raffaelegrandi.pianura.examples.Launcher",
            "Implementation-Title" to "Pianura",
            "Implementation-Version" to project.version,
            "Multi-Release" to "true")      // FlatLaf ships classes for newer JDKs
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
        exclude("module-info.class", "META-INF/versions/*/module-info.class")
        // FlatLaf's license and notice, kept apart from Pianura's
        eachFile { if (path == "META-INF/LICENSE" || path == "META-INF/NOTICE") path = path.replace("META-INF/", "META-INF/flatlaf/") }
    }
    from(rootProject.file("LICENSE")) { into("META-INF") }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
