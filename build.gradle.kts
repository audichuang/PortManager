plugins {
    id("java")
    // Use the recommended alias for the IntelliJ Platform Gradle Plugin
    // alias(libs.plugins.jetbrains.intellij.platform) version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.5.0" // Updated from 2.4.0 to 2.5.0
    id("io.freefair.lombok") version "8.4" // Keep Lombok as requested
}

group = "com.audi.portmanager" // Align group ID with Java packages
version = "1.0.0"

repositories {
    mavenCentral()
    // Default repositories for IntelliJ Platform dependencies
    intellijPlatform.defaultRepositories()
}

dependencies {
    // Lombok for reducing boilerplate code
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")

    // Example dependency - Jackson (optional, remove if not needed)
    // implementation("com.fasterxml.jackson.core:jackson-annotations:2.18.3")

    intellijPlatform {
        // Specify the local IntelliJ IDEA installation path for development.
        // IMPORTANT: Update this path if your IDEA installation is different!
        // Examples:
        // Windows: local("C:/Program Files/JetBrains/IntelliJ IDEA Community Edition 2024.1/")
        // Linux: local("/opt/idea-IC-241.14494.240/")
//        local("/Applications/IntelliJ IDEA.app") // macOS path provided by user
        create("IC", "2024.3")

        // Depend on the Java plugin module, necessary for many Java-related features
        bundledPlugin("com.intellij.java")
    }
}

// Configure Java compilation settings
tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.encoding = "UTF-8" // Ensure consistent encoding
}

// Configure the IntelliJ Platform Gradle Plugin
intellijPlatform {
    // Disable searchable options to avoid Unix domain socket error
    buildSearchableOptions.set(false) // Setting to false to avoid IDE startup during build
    
    // Plugin manifest (plugin.xml) configuration
    pluginConfiguration {
        name.set("Port Manager") // The name of your plugin
        version.set(project.version.toString()) // Use the project version

        vendor {
            name.set("AudiChuang") // Replace with your name or company
            email.set("audiapplication880208@gmail.com") // Replace with your email
            url.set("https://github.com/audichuang") // Replace with your website (optional)
        }

        // Plugin description displayed in the Marketplace and Plugin settings
        description.set("""
            A plugin to easily find and kill processes running on specific ports. Supports macOS and Windows.
        """.trimIndent())

        // Specify compatible IntelliJ Platform versions
        ideaVersion {
            sinceBuild.set("241") // Compatible starting from 2024.1
            // untilBuild.set("243.*") // Example: Compatible up to 2024.3.* (adjust as needed)
        }

        // Release notes for this version
        changeNotes.set("""
            <ul>
                <li>Initial release.</li>
                <li>Find processes by port number.</li>
                <li>Kill selected processes.</li>
                <li>Basic UI using Tool Window.</li>
                <li>Supports macOS and Windows.</li>
            </ul>
        """.trimIndent())
    }

    // 插件驗證配置
    pluginVerification {
        // 指定要驗證的IDE版本
        ides {
            // 使用最新的2024.1版本進行驗證
            ide("IC", "2024.1")
        }
    }
}
