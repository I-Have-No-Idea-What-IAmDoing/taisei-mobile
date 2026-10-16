import org.apache.tools.ant.taskdefs.condition.Os
import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    namespace = "org.taiseiproject"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.taiseiproject"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.4.0"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isDebuggable = true
            isJniDebuggable = true
            packaging {
                jniLibs {
                    keepDebugSymbols.add("**/*.so")
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs(
                "src/main/java",
                "../../subprojects/sdl3/android-project/app/src/main/java"
            )
            jniLibs.srcDirs("src/main/jniLibs")
            assets.srcDirs("src/main/assets")
        }
    }
}

fun getNdkDirectory(): File {
    val localPropsFile = File(rootProject.projectDir, "local.properties")
    val props = Properties()
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { props.load(it) }
        val ndkProp = props.getProperty("ndk.dir")
        if (!ndkProp.isNullOrBlank()) {
            return File(ndkProp.toString())
        }
    }
    val envNdk = System.getenv("ANDROID_NDK_ROOT")
        ?: System.getenv("ANDROID_NDK_HOME")
        ?: System.getenv("ANDROID_NDK")
    if (!envNdk.isNullOrBlank()) {
        return File(envNdk)
    }
    val sdkProp = props.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    if (!sdkProp.isNullOrBlank()) {
        val ndkBaseDir = File(sdkProp, "ndk")
        if (ndkBaseDir.isDirectory) {
            val latestNdk = ndkBaseDir.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }
            if (latestNdk != null) {
                return latestNdk
            }
        }
    }
    throw GradleException("Android NDK location not found. Define 'ndk.dir' in local.properties or set the ANDROID_NDK_ROOT environment variable.")
}

// Custom task to drive Meson & Ninja for each Android ABI
val abis = listOf("arm64-v8a" to "android-arm64")

abis.forEach { (abi, crossName) ->
    val buildDirName = "build-android-$abi"
    val projectRootDir = rootProject.projectDir.parentFile

    val buildDir = File(projectRootDir, buildDirName)
    buildDir.mkdirs()
    val ndkDir = getNdkDirectory()
    val dynamicCrossFile = File(buildDir, "meson-ndk.ini")
    val toolchainCmake = File(ndkDir, "build/cmake/android.toolchain.cmake").absolutePath
    dynamicCrossFile.writeText(
        """
        [properties]
        cmake_toolchain_file = '$toolchainCmake'
        """.trimIndent()
    )

    val mesonTask = tasks.register<Exec>("mesonConfigure_$abi") {
        workingDir = projectRootDir
        val crossFile = File(projectRootDir, "android/cross/$crossName.ini").absolutePath

        val ndkBin = File(ndkDir, "toolchains/llvm/prebuilt/linux-x86_64/bin").absolutePath
        val shaderBin = File(ndkDir, "shader-tools/linux-x86_64").absolutePath
        val currentPath = System.getenv("PATH") ?: ""
        environment("PATH", "$ndkBin:$shaderBin:$currentPath")
        environment("ANDROID_NDK_ROOT", ndkDir.absolutePath)
        environment("ANDROID_NDK_HOME", ndkDir.absolutePath)
        environment("ANDROID_NDK", ndkDir.absolutePath)

        onlyIf { !File(buildDir, "build.ninja").exists() }

        commandLine(
            "meson", "setup", buildDirName,
            "--cross-file", crossFile,
            "--cross-file", dynamicCrossFile.absolutePath,
            "-Dbuildtype=debugoptimized",
            "-Ddeveloper=true",
            "-Dstrip=false",
            "-Dallocator=libc",
            "-Dr_gl33=disabled",
            "-Dr_gles30=enabled",
            "-Dpackage_data=true"
        )
    }

    val ninjaTask = tasks.register<Exec>("ninjaBuild_$abi") {
        dependsOn(mesonTask)
        workingDir = File(projectRootDir, buildDirName)
        val ndkBin = File(ndkDir, "toolchains/llvm/prebuilt/linux-x86_64/bin").absolutePath
        val shaderBin = File(ndkDir, "shader-tools/linux-x86_64").absolutePath
        val currentPath = System.getenv("PATH") ?: ""
        environment("PATH", "$ndkBin:$shaderBin:$currentPath")
        environment("ANDROID_NDK_ROOT", ndkDir.absolutePath)
        environment("ANDROID_NDK_HOME", ndkDir.absolutePath)
        environment("ANDROID_NDK", ndkDir.absolutePath)
        commandLine("ninja")
    }

    val copyNativeLibsTask = tasks.register<Copy>("copyNativeLibs_$abi") {
        dependsOn(ninjaTask)
        from(File(projectRootDir, "$buildDirName/src/libmain.so"))
        val ndkArch = when(abi) {
            "arm64-v8a" -> "aarch64-linux-android"
            "x86_64" -> "x86_64-linux-android"
            else -> "aarch64-linux-android"
        }
        val libcxx = File(ndkDir, "toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/$ndkArch/libc++_shared.so")
        if (libcxx.exists()) {
            from(libcxx)
        }
        into(file("src/main/jniLibs/$abi"))
    }

    tasks.named("preBuild") {
        dependsOn(copyNativeLibsTask)
    }
}

// Task to stage packaged assets (.zip files) into the APK assets directory
tasks.register<Copy>("stageAssets") {
    val projectRootDir = rootProject.projectDir.parentFile
    val buildDirArm64 = File(projectRootDir, "build-android-arm64-v8a")
    val fallbackBuildDirArm64 = File(projectRootDir, "build-android-arm64")
    val resDir = if (File(buildDirArm64, "resources").exists()) {
        File(buildDirArm64, "resources")
    } else {
        File(fallbackBuildDirArm64, "resources")
    }

    from(resDir) {
        include("*.zip")
    }
    into(file("src/main/assets"))
}

tasks.named("preBuild") {
    dependsOn("stageAssets")
}

dependencies {
    implementation("androidx.core:core:1.15.0")
}
