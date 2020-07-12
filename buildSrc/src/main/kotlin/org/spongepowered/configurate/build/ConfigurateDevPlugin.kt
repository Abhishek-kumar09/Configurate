package org.spongepowered.configurate.build

import java.io.File
import java.util.SortedMap
import java.util.TreeMap
import net.ltgt.gradle.errorprone.errorprone
import net.minecrell.gradle.licenser.LicenseExtension
import net.minecrell.gradle.licenser.Licenser
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

internal val BASE_TARGET = JavaVersion.VERSION_1_8
private const val OPTION_PREFIX = "javaHome."
private val MULTIRELEASE_TARGETS = listOf(JavaVersion.VERSION_1_9, JavaVersion.VERSION_1_10)

class ConfigurateDevPlugin : Plugin<Project> {

    private fun availableJavaToolchains(project: Project): SortedMap<JavaVersion, File> {
        return project.properties
                .asSequence()
                .filter { (k, _) -> k.startsWith(OPTION_PREFIX) }
                .map { (optName, path) -> JavaVersion.toVersion(optName.substring(OPTION_PREFIX.length)) to project.file(path!!) }
                .toMap(TreeMap())
    }

    override fun apply(target: Project) {
        with(target) {
            plugins.apply {
                apply(Licenser::class.java)
                apply(JavaLibraryPlugin::class.java)
                apply(ConfiguratePublishingPlugin::class.java)
                apply(CheckstylePlugin::class.java)
                apply("net.ltgt.errorprone")
            }

            // Set up a compile task to build for a specific Java release, including cross-compiling if possible
            val availableToolchains = availableJavaToolchains(target)
            fun JavaCompile.configureForRelease(version: JavaVersion) {
                sourceCompatibility = version.toString()
                targetCompatibility = version.toString()

                val toolchainVersion = availableToolchains.tailMap(version).run { if (isEmpty()) { null } else { firstKey() } } ?: JavaVersion.current()
                if (toolchainVersion < JavaVersion.current()) {
                    options.isFork = true
                    options.forkOptions.javaHome = availableToolchains[toolchainVersion]
                    if (!toolchainVersion.isJava9Compatible) {
                        options.errorprone.isEnabled.set(false)
                    }
                }
                if (toolchainVersion.isJava9Compatible) {
                    options.compilerArgs.addAll(listOf("--release", version.majorVersion))
                }
            }

            tasks.withType(JavaCompile::class.java).configureEach {
                with(it.options) {
                    compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-path", "-Xlint:-serial", "-parameters"))
                    isDeprecation = true
                    it.configureForRelease(BASE_TARGET)
                    encoding = "UTF-8"
                }
            }

            val sourceSets = extensions.getByType(SourceSetContainer::class.java)
            val existingSets = sourceSets.toSet()
            val testTask = tasks.named(JavaPlugin.TEST_TASK_NAME, Test::class.java)
            val checkTask = tasks.named("check")
            val testVersions = mutableSetOf<JavaVersion>()

            // Multi release jars
            existingSets.forEach { set ->
                val jarTask = tasks.findByName(set.jarTaskName) as? Jar ?: return@forEach
                jarTask.manifest.attributes["Multi-Release"] = "true"
                // Based on guidance at https://blog.gradle.org/mrjars
                // and an example at https://github.com/melix/mrjar-gradle

                MULTIRELEASE_TARGETS.forEach { targetVersion ->
                    if (targetVersion <= BASE_TARGET) {
                        throw GradleException("Cannot build version $targetVersion as it is lower than (or equal to?) the project's base version ($BASE_TARGET)")
                    } else if (targetVersion > JavaVersion.current()) {
                        throw GradleException("Java version $targetVersion is required to build this project, and you are running ${JavaVersion.current()}!")
                    }

                    val versionId = "java${targetVersion.majorVersion}"
                    sourceSets.register(set.getTaskName(versionId, null)) { version ->
                        version.java.srcDirs(project.projectDir.resolve("src/${set.name}/$versionId"))
                        // Depend on main source set
                        project.dependencies.add(version.implementationConfigurationName, set.output.classesDirs)?.apply {
                            // this.builtBy(tasks.named(set.compileJavaTaskName))
                        }
                        // Set compatibility
                        tasks.named(version.compileJavaTaskName, JavaCompile::class.java) {
                            it.configureForRelease(targetVersion)
                        }
                        // Add to output jar
                        jarTask.into("META-INF/versions/${targetVersion.majorVersion}") {
                            it.from(version.output)
                        }

                        val toolchainVersion = availableToolchains.tailMap(targetVersion).run { if (isEmpty()) { null } else { firstKey() } }
                        if (toolchainVersion != null && toolchainVersion < JavaVersion.current()) {
                            testVersions += toolchainVersion
                            val versionedTest = tasks.register(set.getTaskName("test", versionId), Test::class.java) {
                                it.dependsOn(jarTask)
                                it.classpath = files(jarTask.archiveFile, it.classpath) - set.output
                                it.executable = availableToolchains[toolchainVersion]?.resolve("bin/java").toString()
                            }
                            checkTask.configure {
                                it.dependsOn(versionedTest)
                            }
                        }
                    }
                }
            }

            val mainJar = tasks.named(JavaPlugin.JAR_TASK_NAME, Jar::class.java)
            val baseToolchain = availableToolchains.tailMap(BASE_TARGET).run { if (isEmpty()) { null } else { firstKey() } }
            if (baseToolchain != null && baseToolchain !in testVersions) {
                testVersions += baseToolchain
                testTask.configure {
                    it.dependsOn(mainJar)
                    it.classpath = files(mainJar.get().archiveFile, it.classpath) - sourceSets.getByName("main").output
                    it.executable = availableToolchains[baseToolchain]?.resolve("bin/java").toString()
                }
            } else {
                testVersions += JavaVersion.current()
            }

            if (JavaVersion.current() !in testVersions) {
                val task = tasks.register("testJava${JavaVersion.current().majorVersion}", Test::class.java) {
                    it.dependsOn(mainJar)
                    it.classpath = files(mainJar.get().archiveFile, it.classpath) - sourceSets.getByName("main").output
                }
                checkTask.configure {
                    it.dependsOn(task)
                }
            }

            tasks.withType(AbstractArchiveTask::class.java).configureEach {
                it.isPreserveFileTimestamps = false
                it.isReproducibleFileOrder = true
            }

            extensions.configure(JavaPluginExtension::class.java) {
                it.withJavadocJar()
                it.withSourcesJar()
                it.sourceCompatibility = BASE_TARGET
                it.targetCompatibility = BASE_TARGET
            }

            tasks.withType(Javadoc::class.java).configureEach {
                it.applyCommonAttributes()
            }

            extensions.configure(LicenseExtension::class.java) {
                with(it) {
                    header = rootProject.file("LICENSE_HEADER")
                    include("**/*.java")
                    include("**/*.kt")
                    newLine = false
                }
            }

            repositories.addAll(listOf(repositories.mavenLocal(), repositories.mavenCentral(), repositories.jcenter()))
            dependencies.apply {
                // error-prone compiler
                add("compileOnly", "com.google.errorprone:error_prone_annotations:${Versions.ERROR_PRONE}")
                add("errorprone", "com.google.errorprone:error_prone_core:${Versions.ERROR_PRONE}")

                // Testing
                add("testImplementation", "org.junit.jupiter:junit-jupiter-api:5.2.0")
                add("testImplementation", "org.junit-pioneer:junit-pioneer:0.1.2")
                add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:5.2.0")
            }

            tasks.withType(Test::class.java).configureEach {
                it.useJUnitPlatform()
            }

            // Checkstyle (based on Sponge config)
            extensions.configure(CheckstyleExtension::class.java) {
                it.toolVersion = "8.32"
                it.configDirectory.set(rootProject.projectDir.resolve("etc/checkstyle"))
                it.configProperties = mapOf(
                        "basedir" to project.projectDir,
                        "severity" to "error"
                )
            }

            extensions.configure(ConfiguratePublishingExtension::class.java) {
                it.publish {
                    from(components.getByName("java"))
                }
            }
        }
    }
}
