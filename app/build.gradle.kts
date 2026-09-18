import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

group = "com.ucas.qingxin"
version = "0.1.0"

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val licensesResourcesRoot = layout.buildDirectory.dir("generated/licenseResources")

val syncLicenses by tasks.registering(Copy::class) {
    from(rootProject.file("LICENSE")) { rename { "PROJECT-LICENSE.txt" } }
    from(rootProject.file("NOTICE")) { rename { "NOTICE.txt" } }
    from(rootProject.file("LICENSES/AGPL-3.0.txt"))
    into(licensesResourcesRoot.map { it.dir("licenses") })
}

sourceSets.main {
    resources.srcDir(licensesResourcesRoot)
}

tasks.named("processResources") {
    dependsOn(syncLicenses)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.json:json:20240303")
    implementation("com.google.zxing:core:3.5.3")
}

compose.desktop {
    application {
        mainClass = "com.ucas.qingxin.signin.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "UCASiClassDesktop"
            packageVersion = project.version.toString()
            description = "Independent UCAS iClass Windows desktop client"
            vendor = "UCAS iClass Desktop contributors"
            licenseFile.set(rootProject.file("LICENSE"))

            windows {
                shortcut = true
                menu = true
                menuGroup = "UCAS iClass Desktop"
                upgradeUuid = "e2491065-3556-4905-9cc4-a6db457724c0"
            }
        }
    }
}
