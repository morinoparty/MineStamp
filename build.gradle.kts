import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.resource.factory)

}

group = "dev.nikomaru"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://jitpack.io")
    maven("https://plugins.gradle.org/m2/")
    maven("https://repo.incendo.org/content/repositories/snapshots")
}

dependencies {
    components {
        // mineauth-api 0.3.x はGradleメタデータ上 JVM 25 ターゲットで公開されているが、
        // 本プロジェクトはJVM 21のため変異(variant)解決が失敗する。
        // compileOnly(API参照のみ)のため、JVM 21互換として扱わせる。
        withModule("party.morino:mineauth-api") {
            allVariants {
                attributes {
                    attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
                }
            }
        }
    }

    compileOnly(libs.paper.api)

    implementation(libs.bundles.commands)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.bundles.coroutines)

    compileOnly(libs.protocolLib)

    // MineAuth連携 (softdepend) — MineAuth本体がランタイムでAPIクラスを提供する
    compileOnly(libs.mineauth.api)

    implementation(libs.koin.core)

    implementation(libs.awsJavaSdkS3)
    implementation(libs.commonsMath3)
    implementation(libs.javaJwt)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}


java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    compileKotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
        compilerOptions.javaParameters = true
    }
    compileTestKotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
    build {
        dependsOn("shadowJar")
    }
    test {
        useJUnitPlatform()
        testLogging {
            showStandardStreams = true
            events("passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
    runServer {
        minecraftVersion("1.21.4")
        val plugins = runPaper.downloadPluginsSpec {
            github("Test-Account666", "PlugManX", "2.4.1","PlugManX-2.4.1.jar")
            url("https://ci.dmulloy2.net/job/ProtocolLib/lastSuccessfulBuild/artifact/build/libs/ProtocolLib.jar")
            github("jpenilla","TabTPS", "v1.3.25","tabtps-spigot-1.3.25.jar")
        }
        downloadPlugins {
            downloadPlugins.from(plugins)
        }
    }
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}


sourceSets.main {
    resourceFactory {
        bukkitPluginYaml {
            name = rootProject.name
            version = "versionPlaceholder" //Don't change
            website = "https://github.com/Nlkomaru/AdvancedShopFinder"
            main = "$group.minestamp.MineStamp"
            apiVersion = "1.20"
            softDepend = listOf("MineAuth")
            libraries = libs.bundles.coroutines.asString()
        }
    }
}

fun Provider<ExternalModuleDependencyBundle>.asString(): List<String> {
    return this.get().map { dependency ->
        "${dependency.group}:${dependency.name}:${dependency.version}"
    }
}
