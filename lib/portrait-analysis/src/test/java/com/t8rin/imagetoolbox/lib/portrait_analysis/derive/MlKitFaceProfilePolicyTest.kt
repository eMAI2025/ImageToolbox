package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitFaceProfilePolicyTest {

    @Test
    fun `profile is rejected on both sides`() {
        listOf(
            awareness(FacePoseMode.PROFILE, FaceImageSide.LEFT, 52f),
            awareness(FacePoseMode.PROFILE, FaceImageSide.RIGHT, -52f)
        ).forEach {
            val result = MlKitFaceProfilePolicy.evaluate(observation(), it)
            assertTrue(FaceGeometryRejectionReason.UNSUPPORTED_PARTIAL_OR_STRONG_PROFILE in result.reasons)
        }
    }

    @Test
    fun `half profile inside yaw band passes profile policy`() {
        listOf(
            awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.LEFT, 15.01f),
            awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.LEFT, 45f),
            awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.RIGHT, -15.01f),
            awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.RIGHT, -45f)
        ).forEach {
            val result = MlKitFaceProfilePolicy.evaluate(observation(), it)
            assertFalse(FaceGeometryRejectionReason.UNSUPPORTED_PARTIAL_OR_STRONG_PROFILE in result.reasons)
            assertFalse(FaceGeometryRejectionReason.VISIBLE_SIDE_CONTRADICTION in result.reasons)
        }
    }

    @Test
    fun `yaw boundaries fail closed outside half profile band`() {
        val frontalBoundary = MlKitFaceProfilePolicy.evaluate(
            observation(), awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.LEFT, 15f)
        )
        val profileBoundary = MlKitFaceProfilePolicy.evaluate(
            observation(), awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.LEFT, 45.01f)
        )
        assertTrue(FaceGeometryRejectionReason.VISIBLE_SIDE_CONTRADICTION in frontalBoundary.reasons)
        assertTrue(FaceGeometryRejectionReason.UNSUPPORTED_PARTIAL_OR_STRONG_PROFILE in profileBoundary.reasons)
    }

    @Test
    fun `yaw sign must match visible image side`() {
        val result = MlKitFaceProfilePolicy.evaluate(
            observation(), awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.RIGHT, 32f)
        )
        assertTrue(FaceGeometryRejectionReason.VISIBLE_SIDE_CONTRADICTION in result.reasons)
    }

    @Test
    fun `missing yaw and unsupported pose quality fail closed`() {
        val missingYaw = MlKitFaceProfilePolicy.evaluate(
            observation(), awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.LEFT, null)
        )
        val excessivePitch = MlKitFaceProfilePolicy.evaluate(
            observation(), awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.LEFT, 32f, pitch = 35.01f)
        )
        val excessiveRoll = MlKitFaceProfilePolicy.evaluate(
            observation(), awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.LEFT, 32f, roll = 40.01f)
        )
        assertTrue(FaceGeometryRejectionReason.MISSING_REQUIRED_POSE_OR_AXIS in missingYaw.reasons)
        assertTrue(FaceGeometryRejectionReason.UNSUPPORTED_PARTIAL_OR_STRONG_PROFILE in excessivePitch.reasons)
        assertTrue(FaceGeometryRejectionReason.UNSUPPORTED_PARTIAL_OR_STRONG_PROFILE in excessiveRoll.reasons)
    }

    private fun awareness(
        poseMode: FacePoseMode,
        side: FaceImageSide,
        yaw: Float?,
        pitch: Float? = 0f,
        roll: Float? = 0f
    ) = FaceRegionAwareness(
        poseMode = poseMode,
        dominantImageSide = side,
        yawDegrees = yaw,
        pitchDegrees = pitch,
        rollDegrees = roll,
        fullFaceGeometryAllowed = poseMode == FacePoseMode.FRONTAL,
        regions = emptyMap(),
        reasons = emptyList()
    )

    private fun observation(): SubjectObservation {
        val point = LandmarkObservation(
            id = "nose_base",
            point = NormalizedPoint3D(0.5f, 0.5f),
            confidence = null,
            confidenceSource = ConfidenceSource.UNAVAILABLE,
            visibility = VisibilityState.VISIBLE,
            backend = ObservationBackend.ML_KIT_FACE_DETECTION
        )
        return SubjectObservation(
            subjectCount = 1,
            faceCount = 1,
            bodyCount = 0,
            landmarks = mapOf(point.id to point)
        )
    }
}
