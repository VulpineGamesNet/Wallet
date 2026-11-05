// Standalone build for the Minecraft 1.12.2 Forge port. It is deliberately NOT
// part of the stonecutter tree at the repository root: 1.12.2 needs Java 8
// bytecode, MCP mappings and ForgeGradle-era tooling (RetroFuturaGradle),
// none of which the modern loom/moddev builds can host. See README.md here.
pluginManagement {
	repositories {
		maven("https://nexus.gtnewhorizons.com/repository/public/") { name = "GTNH" }
		maven("https://maven.minecraftforge.net/") { name = "Forge" }
		gradlePluginPortal()
		mavenCentral()
	}
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "wallet-forge-1.12.2"
