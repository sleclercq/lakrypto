plugins {
    kotlin("multiplatform") version "1.4.21"
}

repositories {
    jcenter()
    mavenCentral()
}

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
    }
    ///disable app icon on macOS
    systemProperty("java.awt.headless", "true")
}
