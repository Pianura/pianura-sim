// Example scenarios. Not part of the published library.
plugins {
    application
}

dependencies {
    implementation(project(":core"))
}

// ./gradlew run               -> example scenario 04, the default demo
// ./gradlew run -Pexample=07  -> any other example scenario (03..09)
val example = providers.gradleProperty("example").getOrElse("04").padStart(2, '0')

application {
    mainClass.set("io.github.raffaelegrandi.pianura.examples.scenario$example.MainClassTest$example")
}
