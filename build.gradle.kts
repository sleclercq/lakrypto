plugins {
    kotlin("multiplatform") version "1.4.21"
    kotlin("plugin.serialization") version "1.4.21"
}

repositories {
    jcenter()
    mavenCentral()
}

val ktorVersion = "1.5.0"

kotlin {
    kotlin {
        jvm()

        sourceSets {
            val commonMain by getting {
                dependencies {
                    implementation(kotlin("stdlib"))
                }
            }
            val jvmMain by getting {
                dependencies {
                    implementation(kotlin("stdlib-jdk8"))
                    implementation("io.ktor:ktor-client-websockets:$ktorVersion")
                    implementation("io.ktor:ktor-client-cio:$ktorVersion")
                    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
                }
            }
        }
    }
}

val run by tasks.creating(JavaExec::class) {
    group = "application"
    main = "MainKt"
    kotlin {
        val main = targets["jvm"].compilations["main"]
        dependsOn(main.compileAllTaskName)
        classpath(
            { main.output.allOutputs.files },
            { configurations["jvmRuntimeClasspath"] }
        )
        //accept console input
        standardInput = System.`in`
    }
    ///disable app icon on macOS
    systemProperty("java.awt.headless", "true")
}
