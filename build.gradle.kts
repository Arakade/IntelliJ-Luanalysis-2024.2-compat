import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.8.21"

    id("org.jetbrains.intellij.platform") version "2.1.0"

    id("org.jetbrains.changelog") version "2.0.0"

    id("de.undercouch.download") version "5.3.0"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
    maven(url = "https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public")
    maven(url = "https://download.jetbrains.com/teamcity-repository")
}

dependencies {
    implementation(fileTree("libs") {
        include("*.jar")
    })

    implementation("org.jetbrains:markdown:0.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.2.0")

    intellijPlatform {
        intellijIdeaCommunity("2024.2.1") // provider { properties("platformVersion") })

        instrumentationTools()

        testFramework(TestFrameworkType.Platform)

        // TODO: This is causing `:test` to fail the build due to not being able to load the following from Maven:
        // - ai.grazie.spell:gec-spell-engine-local-jvm:0.3.103
        // - ai.grazie.nlp:nlp-detect-jvm:0.3.103
        // - ai.grazie.spell:hunspell-en-jvm:0.2.141
        // - ai.grazie.utils:utils-lucene-lt-compatibility-jvm:0.3.103
        // - org.jetbrains.teamcity:serviceMessages:2024.11.1
        testImplementation("com.jetbrains.intellij.java:java-test-framework:242.23726.103")

        // TODO: Think this is no longer needed
//        plugins(properties("platformPlugins")
//            .split(",")
//            .map(String::trim)
//            .filter(String::isNotEmpty)
//        )

        //bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

intellijPlatform {
    pluginConfiguration {
        name = properties("pluginName")
        version = properties("platformVersion")
    }

    pluginVerification {

        ides {
            select {
                //types = IntelliJPlatformType.values().asList() // listOf(IntelliJPlatformType.IntellijIdeaCommunity, IntelliJPlatformType.IntellijIdeaUltimate)
                types = listOf(IntelliJPlatformType.IntellijIdeaCommunity, IntelliJPlatformType.IntellijIdeaUltimate)
                sinceBuild = properties("pluginSinceBuild")
            }
        }
    }

    // TODO: Integrate this in above
//    runPluginVerifier {
//        ideVersions.set(
//            properties("pluginVerifierIdeVersions")
//                .split(',')
//                .map(String::trim)
//                .filter(String::isNotEmpty)
//        )
//     }

    // TODO: downloadSources.set(properties("platformDownloadSources").toBoolean())
    // TODO: updateSinceUntilBuild.set(false)

}

changelog {
    version.set(properties("pluginVersion"))
    groups.set(emptyList<String>())
    repositoryUrl.set(properties("pluginRepositoryUrl"))
}

sourceSets {
    main {
        java {
            srcDirs("gen")
        }
        resources {
            exclude("debugger/**")
        }
    }
}
tasks {
    withType<JavaExec> {
        jvmArgs = listOf("-Xms2048m", "-Xmx8192m")
    }

    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
        kotlinOptions.freeCompilerArgs = listOf(
            "-Xjvm-default=all",
            "-opt-in=kotlin.contracts.ExperimentalContracts"
        )
    }

    patchPluginXml {
        dependsOn("copyEmmyLuaDebugger")
        sinceBuild.set(properties("pluginVersion"))
        changeNotes.set(provider {
            with(changelog) {
                renderItem(
                        getOrNull(properties("pluginVersion"))
                                ?: runCatching { getLatest() }.getOrElse { getUnreleased() },
                        org.jetbrains.changelog.Changelog.OutputType.HTML,
                )
            }
        })
    }

    val debuggerArchitectures = arrayOf("x86", "x64")

    register<Download>("downloadEmmyLuaDebugger") {
        val debuggerVersion = properties("emmyLuaDebuggerVersion")

        src(arrayOf(
            "https://github.com/EmmyLua/EmmyLuaDebugger/releases/download/${debuggerVersion}/emmy_core.so",
            "https://github.com/EmmyLua/EmmyLuaDebugger/releases/download/${debuggerVersion}/emmy_core.dylib",
            *debuggerArchitectures.map {
                "https://github.com/EmmyLua/EmmyLuaDebugger/releases/download/${debuggerVersion}/emmy_core@${it}.zip"
            }.toTypedArray()
        ))

        dest("temp")
    }

    register<Copy>("extractEmmyLuaDebugger") {
        dependsOn("downloadEmmyLuaDebugger")

        debuggerArchitectures.forEach {
            from(zipTree("temp/emmy_core@${it}.zip")) {
                into(it)
            }
        }

        destinationDir = file("temp")
    }

    register<Copy>("copyEmmyLuaDebugger") {
        dependsOn("extractEmmyLuaDebugger")

        // Windows
        debuggerArchitectures.forEach {
            from("temp/${it}/") {
                into("debugger/emmy/windows/${it}")
            }
        }

        // Linux
        from("temp") {
            include("emmy_core.so")
            into("debugger/emmy/linux")
        }

        // Mac
        from("temp") {
            include("emmy_core.dylib")
            into("debugger/emmy/mac")
        }

        destinationDir = file("src/main/resources")
    }

    buildPlugin {
        dependsOn("copyEmmyLuaDebugger")

        val resourcesDir = "src/main/resources"

        from(fileTree(resourcesDir)) {
            include("debugger/**")
            into("/${project.name}/classes")
        }

        from(fileTree(resourcesDir)) {
            include("!!DONT_UNZIP_ME!!.txt")
            into("/${project.name}")
        }
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token.set(System.getenv("PUBLISH_TOKEN"))
        channels.set(listOf(
            properties("pluginVersion")
                .split("-")
                .getOrElse(1) { "default" }
                .split(".")
                .first()
        ))
    }
}
