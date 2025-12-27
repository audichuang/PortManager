plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.5"
    id("io.freefair.lombok") version "8.4"
}

group = "com.audi.portmanager"
version = "1.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // Lombok for reducing boilerplate code
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")

    intellijPlatform {
        create("IC", "2025.2")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "Port Manager"
        version = project.version.toString()

        vendor {
            name = "AudiChuang"
            email = "audiapplication880208@gmail.com"
            url = "https://github.com/audichuang"
        }

        description = """
            A plugin to easily find and kill processes running on specific ports. 
            
            Features:
            • Find processes by port number
            • Kill selected processes with one click
            • Support for macOS and Windows
            • Compatible with ALL JetBrains IDEs (IntelliJ IDEA, PyCharm, WebStorm, PhpStorm, etc.)
            
            Perfect for developers who need to quickly manage port conflicts across different development environments.
        """.trimIndent()

        ideaVersion {
            sinceBuild = "252"
            untilBuild = provider { null }  // No upper limit for future compatibility
        }

        changeNotes = """
            <h3>Version 1.1.0</h3>
            <ul>
                <li><b>NEW:</b> Full compatibility with ALL JetBrains IDEs (PyCharm, WebStorm, PhpStorm, RubyMine, GoLand, etc.)</li>
                <li><b>FIXED:</b> Removed Java module dependency to support non-Java IDEs</li>
                <li><b>IMPROVED:</b> Better cross-IDE compatibility</li>
            </ul>
            
            <h3>Version 1.0.0</h3>
            <ul>
                <li>Initial release</li>
                <li>Find processes by port number</li>
                <li>Kill selected processes</li>
                <li>Basic UI using Tool Window</li>
                <li>Supports macOS and Windows</li>
            </ul>
        """.trimIndent()
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Configure Java toolchain
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

// Configure Java compilation settings
tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
    options.encoding = "UTF-8"
}
