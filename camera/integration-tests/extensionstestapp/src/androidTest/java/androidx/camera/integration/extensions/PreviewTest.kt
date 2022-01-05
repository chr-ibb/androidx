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

package androidx.camera.integration.extensions

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.camera.integration.extensions.util.ExtensionsTestUtil
import androidx.camera.testing.CameraUtil
import androidx.camera.testing.CoreAppTestUtil
import androidx.camera.testing.waitForIdle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private const val BASIC_SAMPLE_PACKAGE = "androidx.camera.integration.extensions"

/**
 * The tests to verify that Preview can work well when extension modes are enabled.
 */
@LargeTest
@RunWith(Parameterized::class)
class PreviewTest(private val cameraId: String, private val extensionMode: Int) {

    @get:Rule
    val useCamera = CameraUtil.grantCameraPermissionAndPreTest()

    @get:Rule
    val storagePermissionRule =
        GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE)!!

    private lateinit var cameraExtensionsActivity: CameraExtensionsActivity

    companion object {
        @Parameterized.Parameters(name = "cameraId = {0}, extensionMode = {1}")
        @JvmStatic
        fun parameters() = ExtensionsTestUtil.getAllCameraIdExtensionModeCombinations()
    }

    @Before
    fun setUp() {
        // Clear the device UI and check if there is no dialog or lock screen on the top of the
        // window before start the test.
        CoreAppTestUtil.prepareDeviceUI(InstrumentationRegistry.getInstrumentation())
    }

    @After
    fun tearDown() {
        if (::cameraExtensionsActivity.isInitialized) {
            cameraExtensionsActivity.finish()
        }
    }

    /**
     * Checks that Preview can successfully enter the STREAMING state to show the preview when an
     * extension mode is enabled.
     */
    @Test
    fun previewWithExtensionModeCanEnterStreamingState() {
        ApplicationProvider.getApplicationContext<Context>().packageManager
            .getLaunchIntentForPackage(BASIC_SAMPLE_PACKAGE)?.apply {
                putExtra(CameraExtensionsActivity.INTENT_EXTRA_CAMERA_ID, cameraId)
                putExtra(CameraExtensionsActivity.INTENT_EXTRA_EXTENSION_MODE, extensionMode)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }.also {
                ActivityScenario.launch<CameraExtensionsActivity>(it).onActivity {
                    cameraExtensionsActivity = it
                }
            }

        // Wait for CameraExtensionsActivity's initialization to be complete
        cameraExtensionsActivity.mInitializationIdlingResource.waitForIdle()

        assumeTrue(cameraExtensionsActivity.isExtensionModeSupported(cameraId, extensionMode))

        // Wait for preview view turned to STREAMING state
        cameraExtensionsActivity.mPreviewViewIdlingResource.waitForIdle()

        // Checks that CameraExtensionsActivity's current extension mode is correct.
        assertThat(cameraExtensionsActivity.currentExtensionMode).isEqualTo(extensionMode)
    }
}