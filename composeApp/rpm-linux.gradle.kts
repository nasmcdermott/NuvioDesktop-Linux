// ---------------------------------------------------------------------------------------------
// Development RPM, Linux x86_64. Packs the jpackage app image into one installable .rpm with the
// host's own rpmbuild, so a Fedora/Bazzite box builds it in a minute instead of the release
// pipeline's hours: no GraalVM native image, no private media runtime, no WebKit build.
// tools/linux/packaging/dev_rpm.py states exactly what that costs, and the artifact is named -dev
// because of it. The release RPM is a different, verified artifact:
// tools/linux/packaging/build.py --format rpm, in the pinned Ubuntu package builder. This file
// never touches it.
//
// Kept in this file so upstream merges only meet the one-line apply() in build.gradle.kts, as with
// appimage-linux.gradle.kts and native-image-linux.gradle.kts. The task is registered on every host
// and enabled on Linux only.
//
//   packageDevRpm   the app image createReleaseDistributable builds, packaged as an RPM
//
// Properties: -Pnuvio.rpm.python=<python3> (default python3).
// ---------------------------------------------------------------------------------------------
import java.util.Properties

val rpmLinuxHost = System.getProperty("os.name").contains("linux", ignoreCase = true)
val rpmScript = rootProject.layout.projectDirectory.file("tools/linux/packaging/dev_rpm.py")
val rpmVersionFile = layout.projectDirectory.file("Configuration/DesktopVersion.properties")
val rpmIconSource = layout.projectDirectory.file("src/desktopMain/resources/icons/nuvio-app-icon.png")
val rpmWorkDir = layout.buildDirectory.dir("rpm-linux")
val rpmPython = providers.gradleProperty("nuvio.rpm.python").getOrElse("python3")

/** The version the app image was stamped with: the same file the packages read. */
fun rpmVersion(): String =
    Properties().apply { rpmVersionFile.asFile.inputStream().use { load(it) } }
        .getProperty("VERSION_NAME").trim()

tasks.register<Exec>("packageDevRpm") {
    group = "distribution"
    description = "Packs the release app image into an installable .rpm for local testing (Linux only)."
    enabled = rpmLinuxHost
    dependsOn("createReleaseDistributable")
    notCompatibleWithConfigurationCache("Runs the host's python3 and rpmbuild.")
    inputs.dir(layout.buildDirectory.dir("compose/binaries/main-release/app/nuvio"))
        .withPropertyName("appImage")
    inputs.file(rpmScript).withPropertyName("packagingScript")
    inputs.file(rpmIconSource).withPropertyName("iconSource")
    outputs.dir(rpmWorkDir).withPropertyName("rpmOutput")
    doFirst {
        val version = rpmVersion()
        val work = rpmWorkDir.get().asFile.apply { mkdirs() }
        val output = work.resolve("nuvio-desktop-$version-dev-x86_64.rpm")
        logger.lifecycle("Packing the release app image into ${output.name} (development RPM, not a release artifact)")
        commandLine(
            rpmPython,
            rpmScript.asFile.absolutePath,
            "--app-image", layout.buildDirectory.dir("compose/binaries/main-release/app/nuvio")
                .get().asFile.absolutePath,
            "--version", version,
            "--work-dir", work.absolutePath,
            "--output", output.absolutePath,
            "--java", File(System.getProperty("java.home"), "bin/java").absolutePath,
            "--icon-source", rpmIconSource.asFile.absolutePath,
        )
    }
    doLast {
        val artifacts = rpmWorkDir.get().asFile.listFiles { file -> file.name.endsWith(".rpm") }
        if (artifacts.isNullOrEmpty()) throw GradleException("No .rpm was produced in $rpmWorkDir")
        artifacts.forEach {
            logger.lifecycle("Development RPM: ${it.absolutePath}")
            logger.lifecycle("Install it with: sudo dnf install ${it.absolutePath}")
        }
    }
}
