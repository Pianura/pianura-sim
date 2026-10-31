// Example scenarios. Not part of the published library.
plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":algorithms"))
}

// Every folder in the examples package is one example, started by its Main class:
//   ./gradlew run                    -> obstacles, the default demo
//   ./gradlew run -Pexample=radio    -> any other example (list in examples/README.md)
val examplesDir = file("src/main/java/io/github/raffaelegrandi/pianura/examples")
val available = examplesDir.listFiles { f -> f.isDirectory }.orEmpty().map { it.name }.sorted()
val example = providers.gradleProperty("example").getOrElse("obstacles")
require(example in available) {
    "Unknown example '$example'. Available: ${available.joinToString(", ")}"
}

application {
    mainClass.set("io.github.raffaelegrandi.pianura.examples.$example.Main")
}
