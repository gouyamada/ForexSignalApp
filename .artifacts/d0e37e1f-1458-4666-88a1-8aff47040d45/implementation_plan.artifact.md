# Implementation Plan - Fix Compilation Errors in `DataSources.kt` and `ForexUI.kt`

Fix annotation import collision in `DataSources.kt` and add missing Compose Material Icons dependency for `ForexUI.kt`.

## User Review Required

> [!IMPORTANT]
> - **Room `@Query` Collision**: In `DataSources.kt`, `@Query` was resolving to Retrofit's `@retrofit2.http.Query` instead of Room's `@androidx.room.Query`. The imports will be disambiguated.
> - **Missing Material Icons**: `Icons.Default.Refresh` requires `androidx.compose.material:material-icons-core`. This dependency will be added to `libs.versions.toml` and `app/build.gradle.kts`.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/t_yamada/MyProjects/AndroidStudioProjects/ForexSignalApp/gradle/libs.versions.toml)
- Add `androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }`.

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/t_yamada/MyProjects/AndroidStudioProjects/ForexSignalApp/app/build.gradle.kts)
- Add `implementation(libs.androidx.compose.material.icons.core)` to `dependencies`.

### App Source Code

#### [MODIFY] [DataSources.kt](file:///C:/Users/t_yamada/MyProjects/AndroidStudioProjects/ForexSignalApp/app/src/main/java/com/gymd/forex/data/DataSources.kt)
- Update imports so Room `@Query` (`androidx.room.Query`) is used for `ForexCacheDao` methods and Retrofit `@Query` (`retrofit2.http.Query`) is qualified for `TwelveDataApiService`.

#### [MODIFY] [ForexUI.kt](file:///C:/Users/t_yamada/MyProjects/AndroidStudioProjects/ForexSignalApp/app/src/main/java/com/gymd/forex/ui/theme/ForexUI.kt)
- Verify `Icons.Default.Refresh` resolves properly once the material-icons library is added.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/t_yamada/MyProjects/AndroidStudioProjects/ForexSignalApp/app/src/main/java/com/gymd/forex/MainActivity.kt)
- Initialize `AppDatabase`, `TwelveDataApiService`, `ForexRepository`, and `ForexSignalViewModel` in `MainActivity` and display `ForexDashboardScreen`.

## Verification Plan

### Automated Tests
- Execute `analyze_file` on `DataSources.kt`, `ForexUI.kt`, and `MainActivity.kt` to ensure 0 errors.
- Execute `gradle_build app:assembleDebug` to confirm the app compiles successfully.

### Manual Verification
- Verify the app launches and displays the FX Dashboard UI.
