/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

import android.content.Context
import android.net.Uri

fun createSilhouetteLabRunner(context: Context): SilhouetteLabRunner = FossSilhouetteLabRunner()

private class FossSilhouetteLabRunner : SilhouetteLabRunner {
    override suspend fun run(uri: Uri): SilhouetteLabRunResult = SilhouetteLabRunResult.Failure(
        message = "Silhouette Lab runtime detectors are available in the Market build only"
    )

    override fun close() = Unit
}
