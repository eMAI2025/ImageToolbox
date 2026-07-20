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

    "marketImplementation"("com.google.mlkit:face-detection:16.1.7")
    "marketImplementation"("com.google.mlkit:face-mesh-detection:16.0.0-beta1")
    "marketImplementation"("com.google.mlkit:pose-detection-accurate:18.0.0-beta5")
    "marketImplementation"(libs.mlkit.segmentation.selfie)

    testImplementation(libs.junit)
}
