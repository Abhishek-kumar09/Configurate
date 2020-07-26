package org.spongepowered.configurate.build

import de.marcphilipp.gradle.nexus.NexusPublishExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension

typealias PublicationConfigureCb = MavenPublication.() -> Unit

open class ConfiguratePublishingExtension {
    internal val configureFunctions = mutableListOf<PublicationConfigureCb>()

    fun publish(cb: PublicationConfigureCb) {
        configureFunctions.add(cb)
    }
}

class ConfiguratePublishingPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            plugins.apply {
                apply("de.marcphilipp.nexus-publish")
                apply("signing")
            }

            group = rootProject.group
            version = rootProject.version

            val configurateExtension = extensions.create("configurate", ConfiguratePublishingExtension::class.java)

            val publishing = extensions.getByType(PublishingExtension::class.java).apply {
                publications { container ->
                    container.register("maven", MavenPublication::class.java) { pub ->
                        with(pub) {
                            pom.apply {
                                artifactId = "configurate-${project.name}"

                                description.set(
                                    "A simple configuration library for Java applications that can handle a variety of formats and " +
                                        "provides a node-based data structure able to handle a wide variety of configuration schemas"
                                )
                                name.set(project.name)
                                url.set("https://github.com/SpongePowered/configurate/")

                                inceptionYear.set("2014")

                                developers {
                                    it.developer { d ->
                                        d.name.set("zml")
                                        d.email.set("zml@spongepowered.org")
                                    }
                                }

                                issueManagement {
                                    it.system.set("GitHub Issues")
                                    it.url.set("https://github.com/SpongePowered/configurate/issues")
                                }

                                licenses { s ->
                                    s.license {
                                        it.name.set("Apache License, Version 2.0")
                                        url.set("https://opensource.org/licenses/Apache-2.0")
                                    }
                                }

                                scm {
                                    it.connection.set("scm:git@github.com:SpongePowered/configurate.git")
                                    it.developerConnection.set("scm:git@github.com:SpongePowered/configurate.git")
                                    it.url.set("https://github.com/SpongePowered/configurate/")
                                }
                            }
                        }
                    }
                }

                if (project.hasProperty("spongeOssrhUsername") && project.hasProperty("spongeOssrhPassword")) {
                    project.extensions.getByType(NexusPublishExtension::class.java).apply {
                        this.repositories.sonatype().apply {
                            username.set(project.property("spongeOssrhUsername") as String)
                            password.set(project.property("spongeOssrhPassword") as String)
                        }
                    }
                }
            }

            val mavenPublication = publishing.publications.named("maven", MavenPublication::class.java)
            afterEvaluate {
                if (
                    it.hasProperty("spongeSnapshotRepo") && it.hasProperty("spongeReleaseRepo") &&
                    it.hasProperty("spongeUsername") && it.hasProperty("spongePassword")
                ) {
                    val isSnapshot = project.version.toString().endsWith("-SNAPSHOT")
                    publishing.repositories { handler ->
                        handler.maven { repo ->
                            repo.name = "GitHubPackages"
                            repo.setUrl("https://maven.pkg.github.com/SpongePowered/Configurate")
                            repo.credentials {
                                it.username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
                                it.password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
                            }
                        }
                        handler.maven { repo ->
                            if (isSnapshot) {
                                repo.setUrl(project.property("spongeSnapshotRepo"))
                            } else {
                                repo.setUrl(project.property("spongeReleaseRepo"))
                            }
                            repo.name = "sponge-new"
                            repo.credentials { cred ->
                                cred.username = project.property("spongeUsername") as String?
                                cred.password = project.property("spongePassword") as String?
                            }
                        }
                    }
                }
                mavenPublication.configure { pub ->
                    for (cb in configurateExtension.configureFunctions) {
                        pub.cb()
                    }
                }
            }

            extensions.configure(SigningExtension::class.java) {
                val spongeSigningKey: String? = it.project.findProperty("spongeSigningKey") as String?
                val spongeSigningPassword: String? = it.project.findProperty("spongeSigningPassword") as String?
                if (spongeSigningKey != null && spongeSigningPassword != null) {
                    val keyFile = file(spongeSigningKey)
                    if (keyFile.exists()) {
                        it.useInMemoryPgpKeys(file(spongeSigningKey).readText(Charsets.UTF_8), spongeSigningPassword)
                    } else {
                        it.useInMemoryPgpKeys(spongeSigningKey, spongeSigningPassword)
                    }
                    it.sign(publishing.publications)
                }
            }

            tasks.withType(Sign::class.java).configureEach {
                it.onlyIf {
                    !project.hasProperty("skipSigning")
                }
            }
        }
    }
}
