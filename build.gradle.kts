/*
 * Copyright 2017-2019 Aljoscha Grebe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.github.benmanes.gradle.versions.updates.gradle.GradleReleaseChannel
import com.palantir.gradle.gitversion.VersionDetails
import groovy.lang.Closure
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Files

plugins {
    kotlin("jvm") apply false
    id("com.github.ben-manes.versions")
    id("com.palantir.git-version")
    id("com.palantir.baseline-exact-dependencies")
}

group = "com.almightyalpaca.jetbrains.plugins.discord"

@Suppress("UNCHECKED_CAST")
val versionDetails = (project.extra["versionDetails"] as Closure<VersionDetails>)()
project.version = versionDetails.lastTag.removePrefix("v") + when {
    versionDetails.isCleanTag -> ""
    else -> "-eap-${versionDetails.commitDistance}"
}

allprojects {
    repositories {
        mavenCentral()
        jcenter()
    }
}

subprojects {
    apply(plugin = "com.palantir.baseline-exact-dependencies")

    group = rootProject.group.toString() + "." + project.name.toLowerCase()
    version = rootProject.version

    val secrets = rootProject.file("secrets.gradle.kts")
    if (secrets.exists()) {
        apply(from = secrets)
    }

    secret("DISCORD_TOKEN")
    secret("BINTRAY_KEY")
    secret("JETBRAINS_TOKEN")

    tasks {
        withType<KotlinCompile> {
            kotlinOptions.jvmTarget = "1.8"
        }
    }
}

fun secret(name: String) {
    if (project.extra.has(name))
        return

    val env: String = System.getenv(name) ?: return

    project.extra[name] = env
}

defaultTasks = mutableListOf("default")

tasks {
    dependencyUpdates {
        gradleReleaseChannel = GradleReleaseChannel.CURRENT.toString()

        rejectVersionIf {
            sequenceOf("alpha", "beta", "rc", "cr", "m", "preview", "eap", "pr")
                .map { qualifier -> Regex(".*[.-]$qualifier[.\\d-_]*", RegexOption.IGNORE_CASE) }
                .any { regex -> regex.matches(candidate.version) }
        }
    }

    withType<Wrapper> {
        distributionType = Wrapper.DistributionType.ALL
        gradleVersion = "5.6.2"
    }

    create<Delete>("clean") {
        group = "build"

        val regex = Regex("""JetBrains-Discord-Integration-Plugin-(\d+).(\d+).(\d+)(?:-eap-(\d+))?.zip""")

        Files.newDirectoryStream(project.projectDir.toPath())
            .filter { p -> regex.matches(p.fileName.toString()) }
            .forEach { p -> delete.add(p) }

        delete.add(project.buildDir)
    }

    create("default") {
        val buildPlugin = project.tasks.getByPath("plugin:buildPlugin") as Zip

        dependsOn(buildPlugin)

        doLast {
            copy {
                from(buildPlugin.outputs)
                into(".")
            }
        }
    }
}
