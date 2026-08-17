import tasks.ReportGenerateTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

group = "org.koitharu"
version = "1.0"

val localConfig = Properties().apply {
    listOf(rootProject.file("local.properties"), rootProject.file("../local.properties"))
        .firstOrNull { it.isFile }
        ?.inputStream()
        ?.use(::load)
}
val parserSecrets = mapOf(
    "ANIME_SLAYER_CLIENT_SECRET" to localConfig.getProperty("anime_slayer_client_secret", ""),
    "ANIME_WITCHER_ALGOLIA_SEARCH_KEY" to localConfig.getProperty("anime_witcher_algolia_search_key", ""),
    "ANIME_WITCHER_FIREBASE_API_KEY" to localConfig.getProperty("anime_witcher_firebase_api_key", ""),
)
fun kotlinString(value: String): String = buildString {
    value.forEach { char ->
        append(
            when (char) {
                '\\' -> "\\\\"
                '"' -> "\\\""
                '\n' -> "\\n"
                '\r' -> "\\r"
                else -> char
            },
        )
    }
}
val parserConfigDir = layout.buildDirectory.dir("generated/parser-config/kotlin")
val generateParserConfig by tasks.registering {
    inputs.properties(parserSecrets)
    outputs.dir(parserConfigDir)
    doLast {
        val output = parserConfigDir.get()
            .file("org/koitharu/kotatsu/parsers/ParserBuildConfig.kt")
            .asFile
        output.parentFile.mkdirs()
        output.writeText(
            buildString {
                appendLine("package org.koitharu.kotatsu.parsers")
                appendLine()
                appendLine("internal object ParserBuildConfig {")
                parserSecrets.forEach { (name, value) ->
                    appendLine("    const val $name = \"${kotlinString(value)}\"")
                }
                appendLine("}")
            },
        )
    }
}

tasks.configureEach {
    if (name.startsWith("ksp", ignoreCase = true)) {
        dependsOn(generateParserConfig)
    }
}

java {
	sourceCompatibility = JavaVersion.VERSION_1_8
	targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.test {
    useJUnitPlatform()
}

ksp {
    arg("summaryOutputDir", "${projectDir}/.github")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateParserConfig)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=org.koitharu.kotatsu.parsers.InternalParsersApi",
        )
    }
}

kotlin {
	jvmToolchain(17)
	compilerOptions {
		// Build with the installed modern JDK while keeping the parser artifact
		// compatible with Android/Java 8 consumers.
		jvmTarget.set(JvmTarget.JVM_1_8)
	}
	explicitApiWarning()
    sourceSets["main"].kotlin.srcDirs("build/generated/ksp/main/kotlin", parserConfigDir)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.okio)
    implementation(libs.json)
    implementation(libs.androidx.collection)
    api(libs.jsoup)

    ksp(project(":kotatsu-parsers-ksp"))

    testImplementation(libs.junit.api)
    testImplementation(libs.junit.engine)
    testImplementation(libs.junit.params)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.quickjs)
}

tasks.register<ReportGenerateTask>("generateTestsReport")
