/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

plugins {
    alias(libs.plugins.image.toolbox.library)
}

android.namespace = "com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit"

dependencies {
    implementation(projects.lib.portraitAnalysis)

    "marketImplementation"(libs.mlkit.face.mesh)
    "marketImplementation"(libs.mlkit.pose.accurate)
    "marketImplementation"(libs.mlkit.segmentation.selfie)

    testImplementation(libs.junit)
}
