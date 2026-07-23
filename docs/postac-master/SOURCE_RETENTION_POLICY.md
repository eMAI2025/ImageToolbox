# Xiaomi source retention policy

## Active project

The five Xiaomi analysis archives are not runtime dependencies and must not be committed to this repository.

They may be removed from the active ChatGPT/project workspace after:

1. the v0.5.0 analysis package has been uploaded and opened successfully;
2. the source archives have been copied to a read-only external archive;
3. checksums of the source archives have been recorded.

## External archive

Retain one immutable copy until at least the first stable editor release. Recommended location:

`POSTAC_MASTER_ARCHIVE/XIAOMI_SOURCE_SNAPSHOT_2026-07-20/`

Recommended contents:

- `01_APK.zip`;
- `04_REPORTS.zip`;
- `com.miui.extraphoto.zip`;
- `com.miui.gallery.zip`;
- `com.miui.mediaeditor.zip`;
- a SHA-256 manifest;
- the reverse-analysis report;
- the v0.5.0 project package and QC log.

## Prohibition

Do not publish or commit Xiaomi/FaceUnity APKs, native libraries, DEX files, model bundles or extracted proprietary assets.

Only independently written documentation, interfaces, tests and implementation code may enter ImageToolbox.
