plugins {
    kotlin("jvm") version "1.9.23"
    application
}

group = "org.starfall"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("io.github.kittinunf.fuel:fuel:2.3.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Agent-Class"] = "Agent"
        attributes["Main-Class"] = "MainKt"
        attributes["Premain-Class"] = "Agent"
        attributes["Can-Retransform-Classes"] = true
        attributes["Can-Redefine-Classes"] = true
    }
    configurations["compileClasspath"].forEach { file: File ->
        from(zipTree(file.absoluteFile))
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    from(sourceSets.main.get().resources)
}

application {
    mainClass.set("MainKt")
}
