// Reference robot controllers: one package per algorithm, built on the core API.
plugins {
    `java-library`
}

base {
    archivesName.set("pianura-algorithms")
}

dependencies {
    api(project(":core"))
}
