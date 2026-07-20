/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

plugins {
    alias(libs.plugins.image.toolbox.library)
    alias(libs.plugins.image.toolbox.feature)
    alias(libs.plugins.image.toolbox.compose)
}

android.namespace = "com.t8rin.imagetoolbox.feature.portrait_lab"

dependencies {
    implementation(projects.lib.portraitAnalysis)
    implementation(projects.lib.portraitAnalysisMlkit)
    implementation(projects.lib.portraitAnalysisMediapipe)

    "marketImplementation"("com.google.mlkit:face-detection:16.1.7")
    "marketImplementation"("com.google.mediapipe:tasks-vision:0.10.35")

    testImplementation(libs.junit)
}
