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

android.namespace = "com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe"

dependencies {
    implementation(projects.lib.portraitAnalysis)

    // Runtime wiring is isolated in this module. Model .task assets are supplied by the app.
    "marketImplementation"("com.google.mediapipe:tasks-vision:0.10.35")

    testImplementation(libs.junit)
}
