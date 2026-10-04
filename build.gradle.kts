plugins {
    java
    alias(libs.plugins.shadow) apply false
}

allprojects {
    group = "net.teujaem"
    repositories {
        mavenCentral()
        maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    }
}
