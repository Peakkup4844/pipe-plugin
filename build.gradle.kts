plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "com.peakkup"
version = "1.0.1"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.tcoded.com/releases")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    implementation("com.tcoded:FoliaLib:0.5.1")

    testImplementation("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.compileJava {
    // Compile against Java 17 API even on a newer JDK so the jar runs on 1.20.1 (Java 17)
    // through the latest (Java 21+).
    options.release.set(17)
    options.encoding = "UTF-8"
}

tasks.processResources {
    filteringCharset = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    // ByteBuddy (Mockito) อาจยังไม่ประกาศรองรับ JDK ใหม่ล่าสุดอย่างเป็นทางการ
    // flag นี้ให้มันรันต่อบน JVM เวอร์ชันที่ยังไม่ถูก whitelist (เช่น JDK 24)
    systemProperty("net.bytebuddy.experimental", "true")
}

tasks.shadowJar {
    archiveClassifier.set("")
    // Relocate FoliaLib so it never clashes with another plugin shading the same lib.
    relocate("com.tcoded.folialib", "com.peakkup.pipeplugin.libs.folialib")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
