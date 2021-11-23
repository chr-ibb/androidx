/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.playground

import org.gradle.api.GradleException
import org.gradle.api.initialization.Settings
import org.gradle.api.model.ObjectFactory
import java.io.File
import java.util.Properties
import javax.inject.Inject

open class PlaygroundExtension @Inject constructor(
    private val settings: Settings,
    private val objectFactory: ObjectFactory
) {
    private var supportRootDir: File? = null

    /**
     * Includes the project if it does not already exist.
     * This is invoked from `includeProject` to ensure all parent projects are included. If they are
     * not, gradle will use the root project path to set the projectDir, which might conflict in
     * playground. Instead, this method checks if another project in that path exists and if so,
     * changes the project dir to avoid the conflict.
     * see b/197253160 for details.
     */
    private fun includeFakeParentProjectIfNotExists(name: String, projectDir: File) {
        if (name.isEmpty()) return
        if (settings.findProject(name) != null) {
            return
        }
        val actualProjectDir: File = if (settings.findProject(projectDir) != null) {
            // Project directory conflicts with an existing project (possibly root). Move it
            // to another directory to avoid the conflict.
            File(projectDir.parentFile, ".ignore-${projectDir.name}")
        } else {
            projectDir
        }
        includeProjectAt(name, actualProjectDir)
        // Set it to a gradle file that does not exist.
        // We must always include projects starting with root, if we are including nested projects.
        settings.project(name).buildFileName = "ignored.gradle"
    }

    private fun includeProjectAt(name: String, projectDir: File) {
        if (settings.findProject(name) != null) {
            throw GradleException("Cannot include project twice: $name is already included.")
        }
        val parentPath = name.substring(0, name.lastIndexOf(":"))
        val parentDir = projectDir.parentFile
        // Make sure parent is created first. see: b/197253160 for details
        includeFakeParentProjectIfNotExists(
            parentPath,
            parentDir
        )
        settings.include(name)
        settings.project(name).projectDir = projectDir
    }

    /**
     * Includes a project by name, with a path relative to the root of AndroidX.
     */
    fun includeProject(name: String, filePath: String) {
        if (supportRootDir == null) {
            throw GradleException("Must call setupPlayground() first.")
        }
        includeProjectAt(name, File(supportRootDir, filePath))
    }

    /**
     * Initializes the playground project to use public repositories as well as other internal
     * projects that cannot be found in public repositories.
     *
     * @param relativePathToRoot The relative path of the project to the root AndroidX project
     */
    fun setupPlayground(relativePathToRoot: String) {
        val projectDir = settings.rootProject.projectDir
        val supportRoot = File(projectDir, relativePathToRoot).canonicalFile
        this.supportRootDir = supportRoot
        val buildFile = File(supportRoot, "playground-common/playground-build.gradle")
        val relativePathToBuild = projectDir.toPath().relativize(buildFile.toPath()).toString()

        val playgroundProperties = Properties()
        val propertiesFile = File(supportRoot, "playground-common/playground.properties")
        playgroundProperties.load(propertiesFile.inputStream())
        settings.gradle.beforeProject { project ->
            // load playground properties. These are not kept in the playground projects to prevent
            // AndroidX build from reading them.
            playgroundProperties.forEach {
                project.extensions.extraProperties[it.key as String] = it.value
            }
        }

        settings.rootProject.buildFileName = relativePathToBuild
        settings.enableFeaturePreview("VERSION_CATALOGS")

        val catalogFiles =
            objectFactory.fileCollection().from("$supportRoot/gradle/libs.versions.toml")
        settings.dependencyResolutionManagement {
            it.versionCatalogs.create("libs").from(catalogFiles)
        }

        includeProject(":lint-checks", "lint-checks")
        includeProject(":lint-checks:integration-tests", "lint-checks/integration-tests")
        includeProject(":fakeannotations", "fakeannotations")
        includeProject(":internal-testutils-common", "testutils/testutils-common")
        includeProject(":internal-testutils-gradle-plugin", "testutils/testutils-gradle-plugin")

        // allow public repositories
        System.setProperty("ALLOW_PUBLIC_REPOS", "true")

        // specify out dir location
        System.setProperty("CHECKOUT_ROOT", supportRoot.path)
    }

    /**
     * A convenience method to include projects from the main AndroidX build using a filter.
     *
     * @param filter This filter will be called with the project name (project path in gradle).
     *               If filter returns true, it will be included in the build.
     */
    fun selectProjectsFromAndroidX(filter: (String) -> Boolean) {
        if (supportRootDir == null) {
            throw RuntimeException("Must call setupPlayground() first.")
        }

        // Multiline matcher for anything of the form:
        //  includeProject(name, path, ...)
        // where '...' is anything except the ')' character.
        /* ktlint-disable max-line-length */
        val includeProjectPattern = Regex(
            """[\n\r\s]*includeProject\("(?<name>[a-z0-9-:]*)",[\n\r\s]*"(?<path>[a-z0-9-\/]+)[^)]+\)$""",
            setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
        ).toPattern()
        val supportSettingsFile = File(supportRootDir, "settings.gradle")
        val matcher = includeProjectPattern.matcher(supportSettingsFile.readText())

        while (matcher.find()) {
            // check if is an include project line, if so, extract project gradle path and
            // file system path and call the filter
            val projectGradlePath = matcher.group("name")
            val projectFilePath = matcher.group("path")
            if (filter(projectGradlePath)) {
                includeProject(projectGradlePath, projectFilePath)
            }
        }
    }

    /**
     * Checks if a project is necessary for playground projects that involve compose.
     */
    fun isNeededForComposePlayground(name: String): Boolean {
        if (name == ":compose:lint:common") return true
        if (name == ":compose:lint:internal-lint-checks") return true
        if (name == ":compose:test-utils") return true
        if (name == ":compose:lint:common-test") return true
        if (name == ":test:screenshot:screenshot") return true
        return false
    }
}