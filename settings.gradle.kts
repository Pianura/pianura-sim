rootProject.name = "pianura"

// core        the simulator: the library that will be published
// algorithms  reference robot controllers, built on core
// examples    example scenarios, runnable with ./gradlew run -Pexample=...
include("core", "algorithms", "examples")
