package nebula.plugin.compile

import nebula.plugin.compile.provider.DefaultLocationJDKPathProvider
import nebula.plugin.compile.provider.EnvironmentJDKPathProvider
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

class JavaCrossCompilePlugin : Plugin<Project> {
    companion object {
        const val RT_JAR_PATH = "jre/lib/rt.jar"
        const val CLASSES_JAR_PATH = "../Classes/classes.jar"

        val providers = listOf(EnvironmentJDKPathProvider(), DefaultLocationJDKPathProvider())
    }

    override fun apply(project: Project) {
        project.plugins.apply(JavaBasePlugin::class.java)
        project.afterEvaluate {
            configureBootstrapClasspath(project)
        }
    }

    private fun configureBootstrapClasspath(project: Project) {
        val convention = project.convention.plugins["java"] as JavaPluginConvention? ?: return
        val targetCompatibility = convention.targetCompatibility
        if (targetCompatibility != JavaVersion.current()) {
            with(project.tasks) {
                withType(JavaCompile::class.java) {
                    it.options.bootClasspath = targetCompatibility.locate().bootClasspath
                }
                withType(GroovyCompile::class.java) {
                    it.options.bootClasspath = targetCompatibility.locate().bootClasspath
                }
                project.plugins.withId("kotlin") {
                    withType(KotlinCompile::class.java) {
                        it.kotlinOptions.jdkHome = targetCompatibility.locate().jdkHome
                    }
                }
            }
        }
    }

    private fun JavaVersion.locate(): JavaLocation {
        val jdkHome = providers
                .map { it.provide(this) }
                .firstOrNull() ?: throw cannotLocate()
        val runtimeJars = listOf(
                File(jdkHome, RT_JAR_PATH),
                File(jdkHome, CLASSES_JAR_PATH)
        )
        val bootClasspath = runtimeJars
                .firstOrNull { it.exists() } ?: throw cannotLocate()
        return JavaLocation(jdkHome, bootClasspath.absolutePath)
    }

    private fun JavaVersion.cannotLocate(): IllegalStateException = IllegalStateException("Could not locate a compatible JDK for target compatibility $this. Change the source/target compatibility, set a JDK_1$majorVersion environment variable with the location, or install to one of the default search locations")

    data class JavaLocation(val jdkHome: String, val bootClasspath: String)
}
