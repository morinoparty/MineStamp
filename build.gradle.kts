/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.resource.factory)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
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
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}

// Paper が起動時に Maven Central から取得するライブラリ。JAR には同梱せず plugin.yml の libraries に列挙する
val library: Configuration by configurations.creating
configurations.compileOnly { extendsFrom(library) }
configurations.testImplementation { extendsFrom(library) }

dependencies {
    compileOnly(libs.paper.api)

    // cloud は Maven Central にないスナップショット版を使うため JAR に同梱する
    implementation(libs.bundles.commands)

    library(libs.kotlin.stdlib)
    // cloud-kotlin-coroutines-annotations が suspend 関数の呼び出しに使う
    library(libs.kotlin.reflect)
    library(libs.kotlinx.serialization.json)
    library(libs.bundles.coroutines)
    // cloud-kotlin-coroutines が利用する
    library(libs.kotlinx.coroutines.jdk8)
    library(libs.koin.core.jvm)
    library(libs.awsSdkS3)
    library(libs.commonsMath3)
    library(libs.javaJwt)

    compileOnly(libs.protocolLib)

    // MineAuth連携 (softdepend) — MineAuth本体がランタイムでAPIクラスを提供する
    compileOnly(libs.mineauth.api)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    compileKotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
        compilerOptions.javaParameters = true
    }
    compileTestKotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
    }
    build {
        dependsOn("shadowJar")
    }
    shadowJar {
        // cloud が推移的に持ち込む Kotlin 系ライブラリは libraries で取得するため同梱しない
        dependencies {
            exclude(dependency("org.jetbrains.kotlin:.*:.*"))
            exclude(dependency("org.jetbrains.kotlinx:.*:.*"))
            exclude(dependency("org.jetbrains:annotations:.*"))
        }
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
        // `-PmcVersion=1.21.11` のように起動するMinecraftバージョンを切り替えられるようにする
        val mcVersion = providers.gradleProperty("mcVersion").getOrElse("26.2")
        minecraftVersion(mcVersion)
        // バージョンごとにワールド等が混ざらないよう実行ディレクトリを分ける
        runDirectory.set(layout.projectDirectory.dir("run/$mcVersion"))
        val plugins =
            runPaper.downloadPluginsSpec {
                github("Test-Account666", "PlugManX", "2.4.1", "PlugManX-2.4.1.jar")
                // dmulloy2 の Jenkins は 403 を返すため GitHub の開発版リリースから取得する
                github("dmulloy2", "ProtocolLib", "dev-build", "ProtocolLib.jar")
                github("jpenilla", "TabTPS", "v1.3.25", "tabtps-spigot-1.3.25.jar")
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
            version = "versionPlaceholder" // Don't change
            website = "https://github.com/Nlkomaru/AdvancedShopFinder"
            main = "$group.minestamp.MineStamp"
            apiVersion = "1.20"
            softDepend = listOf("MineAuth")
            libraries =
                library.dependencies.map { dependency ->
                    "${dependency.group}:${dependency.name}:${dependency.version}"
                }
        }
    }
}

ktlint {
    // 当面は非ゲート（`check`/`build` を失敗させない）。`./gradlew ktlintFormat` で整形する。
    ignoreFailures.set(true)
    filter {
        exclude("**/generated/**")
    }
}

spotless {
    // ktlint / detekt と同様、当面は非ゲート（`check`/`build` を失敗させない）。
    // 開発者が任意に `./gradlew spotlessApply`（一括付与・更新）/ `spotlessCheck`（検証）を
    // 実行する運用とする。
    isEnforceCheck = false

    // ライセンスヘッダーは config/spotless/license-header.kt に一元管理し、「2023-$YEAR」で年の範囲を表す。
    // 開始年が固定された範囲テンプレートのため、updateYearWithLatest は終了年のみを現在年へ更新する
    // （例: 2023-2026 → 2023-2027）。新規ファイルも 2023-現在年 になる。
    val licenseHeader = rootProject.file("config/spotless/license-header.kt")
    kotlin {
        target("src/**/*.kt")
        licenseHeaderFile(licenseHeader).updateYearWithLatest(true)
    }
    kotlinGradle {
        target("*.gradle.kts")
        // .gradle.kts の最初の非ヘッダー行（build: import / settings: rootProject 等）を区切りとする。
        licenseHeaderFile(
            licenseHeader,
            "(import|plugins|pluginManagement|dependencyResolutionManagement|rootProject|@file)"
        ).updateYearWithLatest(true)
    }
}

detekt {
    source.setFrom("src/main/kotlin")
    parallel = true
    buildUponDefaultConfig = true
    // ビルドを失敗させない（レポートのみ）
    ignoreFailures = true
}