package com.clarohome.core.design

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.clarohome.core.model.AppLanguage
import java.util.Locale

fun AppLanguage.toLocale(): Locale = when (this) {
    AppLanguage.ENGLISH -> Locale.ENGLISH
    AppLanguage.SPANISH -> Locale.forLanguageTag("es")
    AppLanguage.HEBREW -> Locale.Builder().setLanguage("iw").build()
}

@SuppressLint("AppBundleLocaleChanges")
fun Context.withClaroLanguage(language: AppLanguage): Context {
    val locale = language.toLocale()
    val config = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(config)
}

@SuppressLint("AppBundleLocaleChanges")
@Composable
fun ClaroLocaleContainer(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val locale = remember(language) { language.toLocale() }
    val layoutDirection = if (language == AppLanguage.HEBREW) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    val localizedConfig = remember(baseConfig, locale) {
        Configuration(baseConfig).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }
    val localizedContext = remember(baseContext, localizedConfig) {
        baseContext.createConfigurationContext(localizedConfig)
    }
    SideEffect {
        Locale.setDefault(locale)
    }
    CompositionLocalProvider(
        LocalConfiguration provides localizedConfig,
        LocalContext provides localizedContext,
        LocalLayoutDirection provides layoutDirection,
        content = content,
    )
}
