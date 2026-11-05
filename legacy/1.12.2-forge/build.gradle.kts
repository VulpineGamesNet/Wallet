import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
	`java-library`
	id("com.gtnewhorizons.retrofuturagradle") version "1.4.9"
	id("com.gradleup.shadow") version "8.3.9"
}

val modId = property("mod_id") as String
val modVersion = property("mod_version") as String
val mcVersion = property("mc_version") as String

group = "net.vulpinegames"
version = "$modVersion-forge+$mcVersion"
base.archivesName.set(modId)

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(8))
	}
	withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
}

minecraft {
	mcVersion.set("1.12.2")
	username.set("Developer")
}

// Everything in `shade` is bundled into the mod jar. 1.12.2 has no jar-in-jar
// and the RLCraft pack ships no JDBC driver, so the DB stack rides along,
// relocated under net.vulpinegames.wallet.shadow so it cannot clash with a
// copy another mod bundles. sqlite-jdbc is NOT relocated: its JNI symbols are
// bound to the org.sqlite class names.
val shade: Configuration by configurations.creating
configurations.implementation.get().extendsFrom(shade)

repositories {
	mavenCentral()
}

dependencies {
	shade("com.zaxxer:HikariCP:4.0.3") // last Java 8 line
	// waffle-jna is Windows-only SSPI auth and drags in JNA, which Minecraft
	// already ships in another version - leave it out.
	shade("org.mariadb.jdbc:mariadb-java-client:3.4.1") { exclude(group = "com.github.waffle") }
	shade("org.xerial:sqlite-jdbc:3.46.1.3")
	// Routes Hikari's slf4j logging into Minecraft's log4j 2.8.1.
	shade("org.apache.logging.log4j:log4j-slf4j-impl:2.8.1") { isTransitive = false }
}

tasks.processResources {
	inputs.property("version", modVersion)
	inputs.property("mcversion", mcVersion)
	filesMatching("mcmod.info") {
		expand("version" to modVersion, "mcversion" to mcVersion)
	}
}

val shadePrefix = "net.vulpinegames.wallet.shadow"
tasks.named<ShadowJar>("shadowJar") {
	configurations = listOf(shade)
	archiveClassifier.set("shadow-dev")
	relocate("com.zaxxer.hikari", "$shadePrefix.hikari")
	relocate("org.mariadb.jdbc", "$shadePrefix.mariadb")
	relocate("org.slf4j", "$shadePrefix.slf4j")
	relocate("org.apache.logging.slf4j", "$shadePrefix.log4jslf4j")
	mergeServiceFiles()
	// FML 1.12 scans every class in a mod jar and logs an error for Java 9+
	// module descriptors; multi-release variants are dead weight on Java 8.
	exclude("module-info.class", "**/module-info.class", "META-INF/versions/**", "META-INF/maven/**")
	exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

// The release jar is the reobfuscated SHADED jar, not the plain one.
tasks.named<com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar>("reobfJar") {
	inputJar.set(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
}

// The deobfuscated (MCP-named) jar without the shaded stack: what addons such as
// WalletAuction compile against.
tasks.jar {
	archiveClassifier.set("dev")
}
