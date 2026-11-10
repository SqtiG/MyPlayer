// Plik główny projektu. Wersje pluginów są zdefiniowane w gradle/libs.versions.toml
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
