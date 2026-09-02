plugins {
	id("mod-platform")
	id("net.neoforged.moddev.legacyforge")
}

stonecutter {
	val (version, loader) = current.project.split('-', limit = 2)
	properties.tags(version, loader)

	replacements.string(current.parsed >= "1.21.11") {
		replace("ResourceLocation", "Identifier")
		replace("location()", "identifier()")
	}
}

platform {
	loader = "forge"
	dependencies {
		required("minecraft") {
			forgeLikeVersionRange = prop("deps.minecraft")
		}
		required("forge") {
			forgeLikeVersionRange.set("[1,)")
		}
	}
}

legacyForge {
	version = "${prop("deps.minecraft")}-${prop("deps.forge")}"

	validateAccessTransformers = true

	accessTransformers.from(
		rootProject.file("src/main/resources/aw/${sc.current.version}.cfg")
	)

	runs {
		register("client") {
			client()
			gameDirectory = file("run/")
			ideName = "Forge Client (${sc.current.version})"
			programArgument("--username=Dev")
		}
		register("server") {
			server()
			gameDirectory = file("run/")
			ideName = "Forge Server (${sc.current.version})"
		}
	}


	mods {
		register(prop("mod.id")) {
			sourceSet(sourceSets["main"])
		}
	}
}

repositories {
	mavenCentral()
	strictMaven("https://api.modrinth.com/maven", "maven.modrinth") { name = "Modrinth" }
}

dependencies {
	// implementation(libs.moulberry.mixinconstraints)
	// jarJar(libs.moulberry.mixinconstraints)
}

sourceSets {
	main {
		resources.srcDir(
			"${rootDir}/versions/datagen/${sc.current.version.split("-")[0]}/src/main/generated"
		)
	}
}

tasks.named("createMinecraftArtifacts") {
	dependsOn(tasks.named("stonecutterGenerate"))
}

// Wallet DB stack. ponytail: jar-in-jar without relocation - swap to shadow+relocate
// only if another mod ever ships a conflicting driver.
dependencies {
	implementation("com.zaxxer:HikariCP:5.1.0")
	implementation("org.mariadb.jdbc:mariadb-java-client:3.4.1")
	jarJar("com.zaxxer:HikariCP:[5.1.0,)")
	jarJar("org.mariadb.jdbc:mariadb-java-client:[3.4.1,)")
	implementation("org.xerial:sqlite-jdbc:3.46.1.3")
	jarJar("org.xerial:sqlite-jdbc:[3.46.1,)")
}
