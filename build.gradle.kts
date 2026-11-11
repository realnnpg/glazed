
plugins {
    id("fabric-loom") version "1.15.5"
}

base {
    archivesName = properties["archives_base_name"] as String
    version = properties["mod_version"] as String
    group = properties["maven_group"] as String
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    maven {
        url = uri("https://jitpack.io")
    }
    maven {
        name = "Bawnorton"
        url = uri("https://maven.bawnorton.com/releases")
    }
    maven {
        name = "babbaj"
        url = uri("https://babbaj.github.io/maven/")
    }
    flatDir {
        dirs("libs")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${properties["minecraft_version"] as String}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${properties["loader_version"] as String}")
    modImplementation("meteordevelopment:meteor-client:${properties["minecraft_version"] as String}-SNAPSHOT")
    modImplementation("meteordevelopment:baritone:${properties["baritone_version"] as String}-SNAPSHOT")
    implementation("com.google.code.gson:gson:2.10.1")
    include(implementation("com.github.LabyStudio:java-spotify-api:${properties["spotify_api_version"] as String}")!!)
    runtimeOnly("dev.babbaj:nether-pathfinder:1.4.1")
    include(implementation(annotationProcessor("com.github.bawnorton.mixinsquared:mixinsquared-fabric:0.3.7-beta.1")!!)!!)
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to project.property("minecraft_version"),
        )
        inputs.properties(propertyMap)
        filteringCharset = "UTF-8"
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }
    jar {
        val licenseSuffix = project.base.archivesName.get()
        from("LICENSE") {
            rename { "${it}_${licenseSuffix}" }
        }
    }
    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
        options.compilerArgs.addAll(listOf("-Xmaxerrs", "10000"))
    }
}
