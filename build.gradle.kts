plugins {
    id("gg.ginco.hygradle")
}

version = "1.0.0"

hytale {
    group = "acorn"
    name = "Thankmas Delivery"
    description = "Thankmas Delivery: NPC present deliveries with private chat dialogue"
    mainClass = "acorn.thankmasdelivery.ThankmasDeliveryPlugin"
    author("Acorn")

    bundleDependencies = true
    includesAssetPack = true
    // serverVersion inherited from hytaleWorkspace

    depends("Hytale:EntityModule", "*")
    depends("Hytale:InteractionModule", "*")
    depends("Hytale:ProjectileModule", "*")
    depends("Hytale:Weather", "*")
    depends("Hytale:NPC", "*")
}

tasks.named<Jar>("jar") {
    archiveBaseName = "thankmasdelivery"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
