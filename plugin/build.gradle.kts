import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
    alias(libs.plugins.shadow)
}

base {
    archivesName.set(rootProject.name)
    version = "${project.version}-plugin"
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly(files("../libs/SP-Framework-1.0.0-plugin-base.jar"))
}

tasks { runServer { minecraftVersion("1.21"); jvmArgs("-Xms512M", "-Xmx1536M") } }

val targetJavaVersion = 21
java {
    sourceCompatibility = JavaVersion.toVersion(targetJavaVersion)
    targetCompatibility = JavaVersion.toVersion(targetJavaVersion)
}
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(targetJavaVersion) }

tasks.shadowJar { archiveClassifier.set("base"); mergeServiceFiles(); configurations = listOf(project.configurations.shadow.get()) }
tasks.register<ShadowJar>("shadowJarAll") { archiveClassifier.set("all"); from(sourceSets.main.get().output); configurations = listOf(project.configurations.runtimeClasspath.get(), project.configurations.shadow.get()); mergeServiceFiles() }
tasks.assemble { dependsOn(tasks.shadowJar, tasks.named("shadowJarAll")) }
tasks.processResources {
    inputs.properties(mapOf("version" to project.version, "spigot_api_version" to project.property("spigot_api_version"), "name" to rootProject.name))
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") { expand(mapOf("version" to project.version, "spigot_api_version" to project.property("spigot_api_version"), "name" to rootProject.name)) }
}
