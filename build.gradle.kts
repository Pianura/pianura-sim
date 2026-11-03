// Settings shared by all modules (core, algorithms, examples).
subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    // Built with any JDK >= 21 (25 LTS recommended); the bytecode and the API used are Java 21,
    // so Pianura runs on every JDK from 21 on.
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(21)
        options.encoding = "UTF-8"
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.11.0"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")   // required since Gradle 9
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
