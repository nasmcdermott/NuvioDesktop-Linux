package com.nuvio.app.features.settings

import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.runtime.key
import com.nuvio.app.core.ui.LocalOpenMetaDetails
import com.nuvio.app.features.music.coverUrl
import nuvio.composeapp.generated.resources.settings_desktop_recent_searches
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import com.nuvio.app.core.perf.PerfTelemetry
import com.nuvio.app.features.library.LibraryQuickAccess
import com.nuvio.app.core.build.AppFeaturePolicy

import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.graphics.Brush
import com.nuvio.app.DesktopFullscreenHoverButtonState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.withFrameNanos
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.WidthFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import com.nuvio.app.core.ui.DesktopNavigationGestureBridge
import com.nuvio.app.core.ui.focusedControl
import com.nuvio.app.core.ui.navigationKey
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.accentGradientMask
import com.nuvio.app.core.ui.AppTheme
import com.nuvio.app.core.ui.arrowKeyFocusNavigation
import com.nuvio.app.core.ui.TextInputFocusTracker
import com.nuvio.app.core.ui.labelRes
import com.nuvio.app.core.ui.LocalNuvioBottomNavigationOverlayPadding
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.isLiquidGlassNativeTabBarSupported
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.platformExitApp
import com.nuvio.app.core.ui.RootNavFocus
import com.nuvio.app.core.ui.secondaryClick
import com.nuvio.app.core.ui.TopBarLeaveRestoresContent
import com.nuvio.app.core.ui.topBarKeyNavigation
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.details.MetaScreenSettingsUiState
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.core.ui.PosterCardStyleUiState
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.debrid.DebridSettings
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.discord.DiscordPresenceSettings
import com.nuvio.app.features.discord.DiscordPresenceSettingsRepository
import com.nuvio.app.features.downloads.DownloadItem
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.downloads.DownloadsUiState
import com.nuvio.app.features.home.HeroBadgePlacement
import com.nuvio.app.features.home.HomeCatalogSettingsItem
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeCatalogSettingsUiState
import com.nuvio.app.features.locallibrary.LocalLibraryRepository
import com.nuvio.app.features.librarypvr.libraryDownloadsSection
import com.nuvio.app.features.mdblist.MdbListSettings
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.games.GameLibrarySettings
import com.nuvio.app.features.screensaver.ScreensaverSettings
import com.nuvio.app.features.games.GameLibrarySettingsRepository
import com.nuvio.app.features.screensaver.ScreensaverSettingsRepository
import com.nuvio.app.features.games.GameModeController
import com.nuvio.app.features.qualicache.QualiCacheSettings
import com.nuvio.app.features.qualicache.QualiCacheSettingsRepository
import com.nuvio.app.features.posterservice.CustomPosterSettings
import com.nuvio.app.features.posterservice.CustomPosterSettingsRepository
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationsRepository
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationsUiState
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.profiles.ActiveProfileMiniAvatar
import com.nuvio.app.features.profiles.AvatarCatalogItem
import com.nuvio.app.features.profiles.AvatarRepository
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.simkl.SimklAuthRepository
import com.nuvio.app.features.simkl.SimklAuthUiState
import com.nuvio.app.features.simkl.SimklConnectionMode
import com.nuvio.app.features.simkl.SimklSettingsRepository
import com.nuvio.app.features.simkl.SimklSettingsUiState
import com.nuvio.app.features.yamtrack.YamtrackSettings
import com.nuvio.app.features.yamtrack.YamtrackSettingsRepository
import com.nuvio.app.features.lights.LightsSettings
import com.nuvio.app.features.lights.LightsSettingsRepository
import com.nuvio.app.features.trakt.TraktAuthUiState
import com.nuvio.app.features.trakt.TraktAuthRepository
import com.nuvio.app.features.trakt.TraktConnectionMode
import com.nuvio.app.features.trakt.TraktCommentsSettings
import com.nuvio.app.features.trakt.TraktSettingsRepository
import com.nuvio.app.features.trakt.TraktSettingsUiState
import com.nuvio.app.features.tmdb.TmdbSettings
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesUiState
import com.nuvio.app.isDesktop
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.*
import nuvio.composeapp.generated.resources.collections_header
import nuvio.composeapp.generated.resources.compose_nav_home
import nuvio.composeapp.generated.resources.calendar_title
import nuvio.composeapp.generated.resources.iptv_settings_page
import nuvio.composeapp.generated.resources.compose_nav_library
import nuvio.composeapp.generated.resources.compose_nav_search
import nuvio.composeapp.generated.resources.compose_settings_page_account
import nuvio.composeapp.generated.resources.compose_settings_page_addons
import nuvio.composeapp.generated.resources.compose_settings_page_advanced
import nuvio.composeapp.generated.resources.compose_settings_page_appearance
import nuvio.composeapp.generated.resources.compose_settings_page_continue_watching
import nuvio.composeapp.generated.resources.compose_settings_page_debrid
import nuvio.composeapp.generated.resources.compose_settings_page_games
import nuvio.composeapp.generated.resources.compose_settings_page_homescreen
import nuvio.composeapp.generated.resources.compose_settings_page_integrations
import nuvio.composeapp.generated.resources.compose_settings_page_keyboard_shortcuts
import nuvio.composeapp.generated.resources.compose_settings_page_licenses_attributions
import nuvio.composeapp.generated.resources.compose_settings_page_mdblist_ratings
import nuvio.composeapp.generated.resources.compose_settings_page_meta_screen
import nuvio.composeapp.generated.resources.compose_settings_page_notifications
import nuvio.composeapp.generated.resources.compose_settings_page_root
import nuvio.composeapp.generated.resources.compose_settings_page_streams
import nuvio.composeapp.generated.resources.compose_settings_page_local_library
import nuvio.composeapp.generated.resources.compose_settings_page_simkl
import nuvio.composeapp.generated.resources.compose_settings_page_tmdb_enrichment
import nuvio.composeapp.generated.resources.compose_settings_page_trakt
import nuvio.composeapp.generated.resources.sidebar_library
import nuvio.composeapp.generated.resources.sidebar_search
import nuvio.composeapp.generated.resources.compose_settings_page_video_audio
import nuvio.composeapp.generated.resources.compose_settings_page_player
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.nuvio.app.core.i18n.localizedString

/**
 * The settings page last shown, so `(PerfScreen)` can name it when the Settings tab is re-entered
 * with its composition retained (the page effect does not re-run then).
 */
@Volatile
internal var perfSettingsPage: String = SettingsPage.Addons.name

private val SettingsSearchRevealThreshold = 28.dp
private const val SettingsSearchRevealAnimationMillis = 240L
private const val SettingsSearchRevealHapticDelayMillis = 90L
// Placeholder GitHub target for the sidebar footer link — no dedicated Nuvio HTPC repo exists yet.

private val DesktopSettingsSidebarWidth = 244.dp
// Widened 20% from 775.dp: the denser pages (stream scoring's label + stepper rows in particular)
// were running out of horizontal room for their captions.
private val DesktopSettingsMainColumnWidth = 930.dp
private val DesktopSettingsContextPanelWidth = 300.dp

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    rootActionRequests: Flow<Unit> = emptyFlow(),
    requestedPageName: String? = null,
    onRequestedPageConsumed: () -> Unit = {},
    rootActionsEnabled: Boolean = true,
    onSwitchProfile: (() -> Unit)? = null,
    onHomescreenClick: () -> Unit = {},
    onMetaScreenClick: () -> Unit = {},
    onContinueWatchingClick: () -> Unit = {},
    onAddonsClick: () -> Unit = {},
    onPluginsClick: () -> Unit = {},
    onOpenDownload: (DownloadItem) -> Unit = {},
    onOpenPartyMedia: (com.nuvio.app.features.party.PartyMedia) -> Unit = {},
    onAccountClick: () -> Unit = {},
    onSupportersContributorsClick: () -> Unit = {},
    onLicensesAttributionsClick: () -> Unit = {},
    onCheckForUpdatesClick: (() -> Unit)? = null,
    onShowLatestChangelogClick: (() -> Unit)? = null,
    onOpenCollectionEditor: (String?) -> Unit = {},
    onNavigateToHome: (() -> Unit)? = null,
    onNavigateToSearch: (() -> Unit)? = null,
    onNavigateToLibrary: (() -> Unit)? = null,
    onNavigateToDiscover: (() -> Unit)? = null,
    onNavigateToCalendar: (() -> Unit)? = null,
    onNavigateToLiveTv: (() -> Unit)? = null,
    onNavigateToMusic: (() -> Unit)? = null,
) {
    val homeKeyFocusRequester = remember { FocusRequester() }
    var settingsSearchHasFocus by remember { mutableStateOf(false) }
    // Any editable field on the page (e.g. a Local Library catalog name) suppresses the shortcut so
    // typing "H" doesn't jump to Home. The search bar keeps its own flag for the same reason.
    val textInputActive by TextInputFocusTracker.active.collectAsStateWithLifecycle()
    // H returns to the home tab. onKeyEvent (bubble phase) so settings text fields,
    // which consume their own keystrokes, are never disrupted.
    // The page root holds focus when Settings opens. An arrow or Select while the root itself has focus
    // hands focus to the selected sidebar row (the page's entry point); Back while it holds the keys is
    // passed to the app-level handler, which goes up a level or leaves Settings.
    val settingsEntryRequester = remember { FocusRequester() }
    val entryFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var settingsRootFocused by remember { mutableStateOf(false) }
    val homeKeyModifier = if (isDesktop && onNavigateToHome != null) {
        Modifier
            .focusRequester(homeKeyFocusRequester)
            // Before focusable(), not after: Compose reports focus to an onFocusChanged that sits
            // outside the focus target, so a root observing it afterwards never sees its own focus
            // and the entry key below never fires.
            .onFocusChanged { settingsRootFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                val action = settingsNavKeyAction(event.navigationKey())
                val enterPage = event.type == KeyEventType.KeyDown &&
                    settingsRootEntersPage(
                        rootFocused = settingsRootFocused,
                        textInputActive = textInputActive,
                        searchFocused = settingsSearchHasFocus,
                        action = action,
                    )
                if (enterPage) {
                    runCatching { settingsEntryRequester.requestFocus() }
                    true
                } else if (event.type == KeyEventType.KeyDown && settingsRootFocused &&
                    action == SettingsNavKeyAction.Back && !textInputActive
                ) {
                    // Back with the page root holding the keys: a sub-page goes up a level, the
                    // root leaves Settings. The app-level ladder decides which.
                    DesktopNavigationGestureBridge.requestBack()
                    true
                } else if (event.type == KeyEventType.KeyDown && event.key == Key.H && !settingsSearchHasFocus && !textInputActive) {
                    onNavigateToHome(); true
                } else {
                    false
                }
            }
    } else {
        Modifier
    }
    // The root holds the keys when Settings opens, so the first arrow hands them to the sidebar
    // row. Nothing requested it on a plain tab entry (the page-change effect only runs when the
    // page changes), which left focus null when Settings was opened from the homepage. Only when
    // nothing has already taken the keys, so a requested/search page keeps its own entry focus.
    LaunchedEffect(Unit) {
        if (!isDesktop) return@LaunchedEffect
        withFrameNanos { }
        if (entryFocusManager.focusedControl() == null) {
            runCatching { homeKeyFocusRequester.requestFocus() }
        }
    }

    // Re-requested when game mode closes over Settings, not just on first composition: its library
    // holds focus while it is up, and Compose leaves nothing focused once that focusable goes —
    // which silently kills H and every other single-key shortcut until something is clicked.
    val gameModeActive by GameModeController.active.collectAsStateWithLifecycle()
    LaunchedEffect(gameModeActive) {
        if (!gameModeActive && isDesktop && onNavigateToHome != null) {
            runCatching { homeKeyFocusRequester.requestFocus() }
        }
    }

    BoxWithConstraints(
        modifier = modifier.then(homeKeyModifier).fillMaxSize(),
    ) {
        val playerSettingsUiState by remember {
            PlayerSettingsRepository.ensureLoaded()
            PlayerSettingsRepository.uiState
        }.collectAsStateWithLifecycle()

        val selectedTheme by remember {
            ThemeSettingsRepository.ensureLoaded()
            ThemeSettingsRepository.selectedTheme
        }.collectAsStateWithLifecycle()
        val customTheme by remember { ThemeSettingsRepository.customTheme }.collectAsStateWithLifecycle()
        val amoledEnabled by remember { ThemeSettingsRepository.amoledEnabled }.collectAsStateWithLifecycle()
        val liquidGlassNativeTabBarEnabled by remember {
            ThemeSettingsRepository.liquidGlassNativeTabBarEnabled
        }.collectAsStateWithLifecycle()
        val desktopColumnGuidesVisible by remember {
            ThemeSettingsRepository.desktopColumnGuidesVisible
        }.collectAsStateWithLifecycle()
        val desktopSettingsFullWidth by remember {
            ThemeSettingsRepository.desktopSettingsFullWidth
        }.collectAsStateWithLifecycle()
        val desktopNavigationLayout by remember {
            ThemeSettingsRepository.desktopNavigationLayout
        }.collectAsStateWithLifecycle()
        val desktopAppUiScalePercent by remember {
            ThemeSettingsRepository.desktopAppUiScalePercent
        }.collectAsStateWithLifecycle()
        val desktopAppUiScaleAppliesToDetails by remember {
            ThemeSettingsRepository.desktopAppUiScaleAppliesToDetails
        }.collectAsStateWithLifecycle()
        val liquidGlassNativeTabBarSupported = remember { isLiquidGlassNativeTabBarSupported() }
        val selectedAppLanguage by remember { ThemeSettingsRepository.selectedAppLanguage }.collectAsStateWithLifecycle()
        val tmdbSettings by remember {
            TmdbSettingsRepository.ensureLoaded()
            TmdbSettingsRepository.uiState
        }.collectAsStateWithLifecycle()
        val mdbListSettings by remember {
            MdbListSettingsRepository.ensureLoaded()
            MdbListSettingsRepository.uiState
        }.collectAsStateWithLifecycle()
        val qualiCacheSettings by remember {
            QualiCacheSettingsRepository.ensureLoaded()
            QualiCacheSettingsRepository.uiState
        }.collectAsStateWithLifecycle()
        val customPosterSettings by remember {
            CustomPosterSettingsRepository.ensureLoaded()
            CustomPosterSettingsRepository.uiState
        }.collectAsStateWithLifecycle()
        val gameLibrarySettings by remember {
            GameLibrarySettingsRepository.ensureLoaded()
            GameLibrarySettingsRepository.uiState
        }.collectAsStateWithLifecycle()
        val screensaverSettings by remember {
            ScreensaverSettingsRepository.ensureLoaded()
            ScreensaverSettingsRepository.uiState
        }.collectAsStateWithLifecycle()
        val debridSettings by remember {
            DebridSettingsRepository.ensureLoaded()
            DebridSettingsRepository.uiState
        }.collectAsStateWithLifecycle()
        val discordPresenceSettings by remember {
            DiscordPresenceSettingsRepository.ensureLoaded()
            DiscordPresenceSettingsRepository.uiState
        }.collectAsStateWithLifecycle()
        val traktAuthUiState by remember {
            TraktAuthRepository.ensureLoaded()
            TraktAuthRepository.uiState
        }.collectAsStateWithLifecycle()
        val simklAuthUiState by remember {
            SimklAuthRepository.ensureLoaded()
            SimklAuthRepository.uiState
        }.collectAsStateWithLifecycle()
        val simklSettingsUiState by remember {
            SimklSettingsRepository.ensureLoaded()
            SimklSettingsRepository.uiState
        }.collectAsStateWithLifecycle()
        val yamtrackSettingsUiState by remember {
            YamtrackSettingsRepository.ensureLoaded()
            YamtrackSettingsRepository.uiState
        }.collectAsStateWithLifecycle()
        val lightsSettingsUiState by remember {
            LightsSettingsRepository.ensureLoaded()
            LightsSettingsRepository.uiState
        }.collectAsStateWithLifecycle()
        val traktCommentsEnabled by remember {
            TraktCommentsSettings.ensureLoaded()
            TraktCommentsSettings.enabled
        }.collectAsStateWithLifecycle()
        val traktSettingsUiState by remember {
            TraktSettingsRepository.ensureLoaded()
            TraktSettingsRepository.uiState
        }.collectAsStateWithLifecycle()
        val addonsUiState by remember {
            AddonRepository.initialize()
            AddonRepository.uiState
        }.collectAsStateWithLifecycle()
        val downloadsUiState by remember {
            DownloadsRepository.ensureLoaded()
            DownloadsRepository.uiState
        }.collectAsStateWithLifecycle()
        val homescreenCatalogRefreshKey = remember(addonsUiState.addons) {
            val enabledAddons = addonsUiState.addons.enabledAddons()
            val allManifestsSettled = enabledAddons.isNotEmpty() &&
                enabledAddons.none { it.isRefreshing }
            if (!allManifestsSettled) return@remember emptyList<String>()
            enabledAddons.mapNotNull { addon ->
                val manifest = addon.manifest ?: return@mapNotNull null
                buildString {
                    append(manifest.transportUrl)
                    append(':')
                    append(manifest.catalogs.joinToString(separator = ",") { catalog ->
                        "${catalog.type}:${catalog.id}:${catalog.extra.count { it.isRequired }}"
                    })
                }
            }
        }
        val homescreenSettingsUiState by remember {
            HomeCatalogSettingsRepository.snapshot()
            HomeCatalogSettingsRepository.uiState
        }.collectAsStateWithLifecycle()
        val collections by CollectionRepository.collections.collectAsStateWithLifecycle()
        val metaScreenSettingsUiState by remember {
            MetaScreenSettingsRepository.ensureLoaded()
            MetaScreenSettingsRepository.uiState
        }.collectAsStateWithLifecycle()
        val continueWatchingPreferencesUiState by remember {
            ContinueWatchingPreferencesRepository.ensureLoaded()
            ContinueWatchingPreferencesRepository.uiState
        }.collectAsStateWithLifecycle()
        val posterCardStyleUiState by remember {
            PosterCardStyleRepository.ensureLoaded()
            PosterCardStyleRepository.uiState
        }.collectAsStateWithLifecycle()
        val episodeReleaseNotificationsUiState by remember {
            EpisodeReleaseNotificationsRepository.ensureLoaded()
            EpisodeReleaseNotificationsRepository.uiState
        }.collectAsStateWithLifecycle()
        val profileSettingsState by remember {
            ProfileRepository.state
        }.collectAsStateWithLifecycle()
        val profileAvatars by AvatarRepository.avatars.collectAsStateWithLifecycle()

        LaunchedEffect(homescreenCatalogRefreshKey) {
            if (homescreenCatalogRefreshKey.isEmpty()) return@LaunchedEffect
            HomeCatalogSettingsRepository.syncCatalogs(addonsUiState.addons.enabledAddons())
        }

        LaunchedEffect(Unit) {
            CollectionRepository.initialize()
        }

        LaunchedEffect(collections) {
            HomeCatalogSettingsRepository.syncCollections(collections)
        }

        var currentPage by rememberSaveable { mutableStateOf(SettingsPage.Addons.name) }
        LaunchedEffect(currentPage) {
            perfSettingsPage = currentPage
            PerfTelemetry.markScreen("Settings/$currentPage")
        }
        var selectedDownloadsShowId by rememberSaveable { mutableStateOf<String?>(null) }
        var pendingDownloadsDelete by remember { mutableStateOf<DownloadsSettingsDeleteTarget?>(null) }
        val scrollToTopRequests = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
        val page = remember(currentPage) { SettingsPage.valueOf(currentPage) }
        val previousPage = page.desktopBackPage()
        val pendingSettingsAnchor by SettingsScrollAnchor.requested.collectAsStateWithLifecycle()
        val pendingTitleHighlight by SettingsScrollAnchor.titleHighlight.collectAsStateWithLifecycle()

        // A result whose row is absent on this platform must not remain armed indefinitely and
        // unexpectedly scroll some later page. Mounted destination rows normally consume it on
        // their first frame; this only expires unresolved requests.
        LaunchedEffect(pendingSettingsAnchor) {
            val request = pendingSettingsAnchor ?: return@LaunchedEffect
            delay(1_500L)
            SettingsScrollAnchor.expire(request.sequence)
        }
        LaunchedEffect(pendingTitleHighlight) {
            val highlight = pendingTitleHighlight ?: return@LaunchedEffect
            delay(SettingsScrollAnchorHighlightMillis)
            SettingsScrollAnchor.expireTitleHighlight(highlight.sequence)
        }

        LaunchedEffect(page) {
            // Leaving a page drops any text-input shortcut lock, so a field left focused (e.g. a
            // catalog name box) can't keep navigation shortcuts suppressed on the next page.
            TextInputFocusTracker.reset()
            if (!page.isEnabledByFeaturePolicy()) {
                currentPage = SettingsPage.Addons.name
            }
        }

        LaunchedEffect(rootActionRequests, rootActionsEnabled, page) {
            rootActionRequests.collect {
                if (!rootActionsEnabled) return@collect
                val pageToOpen = page.desktopBackPage()
                if (pageToOpen != null) {
                    currentPage = pageToOpen.name
                } else {
                    scrollToTopRequests.tryEmit(Unit)
                }
                // Re-selecting the Settings tab is still an entry: take the keys even if a
                // persistent control elsewhere (the music bar) was holding them.
                if (isDesktop) {
                    withFrameNanos { }
                    runCatching { homeKeyFocusRequester.requestFocus() }
                }
            }
        }

        LaunchedEffect(requestedPageName, rootActionsEnabled) {
            val targetPage = requestedPageName
                ?.let { runCatching { SettingsPage.valueOf(it) }.getOrNull() }
                ?: return@LaunchedEffect
            if (!rootActionsEnabled) return@LaunchedEffect
            if (targetPage.isEnabledByFeaturePolicy()) {
                currentPage = targetPage.name
            }
            onRequestedPageConsumed()
        }

        PlatformBackHandler(
            enabled = rootActionsEnabled && previousPage != null,
            onBack = {
                val dest = SettingsScrollAnchor.consumeBackTo() ?: previousPage
                dest?.let { currentPage = it.name }
            },
        )

        // Desktop Back (Backspace, gamepad B, browser Back, mouse Back) reaches the app-level handler
        // first. It asks this page to go up a level, and goes to Home only when the page has no parent.
        val backBridgeToken = remember { Any() }
        DisposableEffect(previousPage, rootActionsEnabled) {
            SettingsBackBridge.register(backBridgeToken) {
                if (!rootActionsEnabled) return@register false
                when (val target = settingsBackTarget(SettingsScrollAnchor.consumeBackTo(), previousPage)) {
                    is SettingsBackTarget.Page -> {
                        currentPage = target.page.name
                        true
                    }
                    SettingsBackTarget.Home -> false
                }
            }
            onDispose { SettingsBackBridge.unregister(backBridgeToken) }
        }

        if (maxWidth >= 768.dp) {
            // A page merged into another (Plugins, Continue Watching, Downloads) opens its host.
            // Rewritten in state as well, so back, the sidebar highlight and the saved page agree;
            // the host is passed straight away so the old page never draws for a frame first.
            val mergedInto = page.desktopMergedInto()
            if (mergedInto != null) {
                LaunchedEffect(page) { currentPage = mergedInto.name }
            }
            TabletSettingsScreen(
                page = mergedInto ?: page,
                entryRequester = settingsEntryRequester,
                scrollToTopRequests = scrollToTopRequests,
                onPageChange = { currentPage = it.name },
                showContextPanel = maxWidth >= 1180.dp,
                showLoadingOverlay = playerSettingsUiState.showLoadingOverlay,
                defaultPlaybackSpeed = playerSettingsUiState.defaultPlaybackSpeed,
                preferredAudioLanguage = playerSettingsUiState.preferredAudioLanguage,
                secondaryPreferredAudioLanguage = playerSettingsUiState.secondaryPreferredAudioLanguage,
                preferredSubtitleLanguage = playerSettingsUiState.preferredSubtitleLanguage,
                secondaryPreferredSubtitleLanguage = playerSettingsUiState.secondaryPreferredSubtitleLanguage,
                streamReuseLastLinkEnabled = playerSettingsUiState.streamReuseLastLinkEnabled,
                streamReuseLastLinkCacheHours = playerSettingsUiState.streamReuseLastLinkCacheHours,
                decoderPriority = playerSettingsUiState.decoderPriority,
                mapDV7ToHevc = playerSettingsUiState.mapDV7ToHevc,
                tunnelingEnabled = playerSettingsUiState.tunnelingEnabled,
                useLibass = playerSettingsUiState.useLibass,
                libassRenderType = playerSettingsUiState.libassRenderType,
                rememberLastProfileEnabled = profileSettingsState.rememberLastProfileEnabled,
                selectedTheme = selectedTheme,
                onThemeSelected = ThemeSettingsRepository::setTheme,
                customTheme = customTheme,
                amoledEnabled = amoledEnabled,
                onAmoledToggle = ThemeSettingsRepository::setAmoled,
                liquidGlassNativeTabBarSupported = liquidGlassNativeTabBarSupported,
                liquidGlassNativeTabBarEnabled = liquidGlassNativeTabBarEnabled,
                onLiquidGlassNativeTabBarToggle = ThemeSettingsRepository::setLiquidGlassNativeTabBar,
                desktopNavigationLayout = desktopNavigationLayout,
                onDesktopNavigationLayoutSelected = ThemeSettingsRepository::setDesktopNavigationLayout,
                desktopAppUiScalePercent = desktopAppUiScalePercent,
                onDesktopAppUiScalePercentChange = ThemeSettingsRepository::setDesktopAppUiScalePercent,
                desktopAppUiScaleAppliesToDetails = desktopAppUiScaleAppliesToDetails,
                onDesktopAppUiScaleAppliesToDetailsChange = ThemeSettingsRepository::setDesktopAppUiScaleAppliesToDetails,
                desktopColumnGuidesVisible = desktopColumnGuidesVisible,
                onDesktopColumnGuidesVisibleChange = ThemeSettingsRepository::setDesktopColumnGuidesVisible,
                desktopSettingsFullWidth = desktopSettingsFullWidth,
                onDesktopSettingsFullWidthChange = ThemeSettingsRepository::setDesktopSettingsFullWidth,
                selectedAppLanguage = selectedAppLanguage,
                onAppLanguageSelected = ThemeSettingsRepository::setAppLanguage,
                episodeReleaseNotificationsUiState = episodeReleaseNotificationsUiState,
                tmdbSettings = tmdbSettings,
                mdbListSettings = mdbListSettings,
                qualiCacheSettings = qualiCacheSettings,
                customPosterSettings = customPosterSettings,
                gameLibrarySettings = gameLibrarySettings,
                screensaverSettings = screensaverSettings,
                debridSettings = debridSettings,
                discordPresenceSettings = discordPresenceSettings,
                traktAuthUiState = traktAuthUiState,
                traktCommentsEnabled = traktCommentsEnabled,
                traktSettingsUiState = traktSettingsUiState,
                simklAuthUiState = simklAuthUiState,
                simklSettingsUiState = simklSettingsUiState,
                yamtrackSettingsUiState = yamtrackSettingsUiState,
                lightsSettingsUiState = lightsSettingsUiState,
                homescreenHeroEnabled = homescreenSettingsUiState.heroEnabled,
                homescreenHeroInfoLines = homescreenSettingsUiState.heroInfoLines,
                homescreenHeroInfoPriority = homescreenSettingsUiState.heroInfoPriority,
                homescreenHeroBadgePlacement = homescreenSettingsUiState.heroBadgePlacement,
                homescreenHeroBadgeScale = homescreenSettingsUiState.heroBadgeScale,
                homescreenHeroReleaseStatusUnavailableOnly = homescreenSettingsUiState.heroReleaseStatusUnavailableOnly,
                homescreenHideUnreleasedContent = homescreenSettingsUiState.hideUnreleasedContent,
                homescreenHideWatchedContent = homescreenSettingsUiState.hideWatchedContent,
                homescreenHideCatalogUnderline = homescreenSettingsUiState.hideCatalogUnderline,
                homescreenCatalogRowShuffleEnabled = homescreenSettingsUiState.catalogRowShuffleEnabled,
                homescreenAdaptiveHeroEnabled = homescreenSettingsUiState.adaptiveHeroEnabled,
                homescreenAdaptiveHeroVerticalBias = homescreenSettingsUiState.adaptiveHeroVerticalBias,
                homescreenHeroAmbientBackgroundEnabled = homescreenSettingsUiState.heroAmbientBackgroundEnabled,
                homescreenTvModeEnabled = homescreenSettingsUiState.tvModeEnabled,
                homescreenItems = homescreenSettingsUiState.items,
                randomPlaySettingsUiState = homescreenSettingsUiState,
                metaScreenSettingsUiState = metaScreenSettingsUiState,
                continueWatchingPreferencesUiState = continueWatchingPreferencesUiState,
                posterCardStyleUiState = posterCardStyleUiState,
                profileAvatars = profileAvatars,
                onSwitchProfile = onSwitchProfile,
                downloadsUiState = downloadsUiState,
                selectedDownloadsShowId = selectedDownloadsShowId,
                onSelectedDownloadsShowChange = { selectedDownloadsShowId = it },
                onOpenDownload = onOpenDownload,
                onOpenPartyMedia = onOpenPartyMedia,
                onDownloadsDeleteTarget = { pendingDownloadsDelete = it },
                onSupportersContributorsClick = onSupportersContributorsClick,
                onLicensesAttributionsClick = onLicensesAttributionsClick,
                onCheckForUpdatesClick = onCheckForUpdatesClick,
                onShowLatestChangelogClick = onShowLatestChangelogClick,
                onOpenCollectionEditor = onOpenCollectionEditor,
                onNavigateToHome = onNavigateToHome,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToLibrary = onNavigateToLibrary,
                onNavigateToDiscover = onNavigateToDiscover,
                onNavigateToCalendar = onNavigateToCalendar,
                onNavigateToLiveTv = onNavigateToLiveTv,
                onNavigateToMusic = onNavigateToMusic,
                onSettingsSearchFocusChange = { settingsSearchHasFocus = it },
            )
        } else {
            MobileSettingsScreen(
                page = page,
                scrollToTopRequests = scrollToTopRequests,
                onPageChange = { currentPage = it.name },
                showLoadingOverlay = playerSettingsUiState.showLoadingOverlay,
                defaultPlaybackSpeed = playerSettingsUiState.defaultPlaybackSpeed,
                preferredAudioLanguage = playerSettingsUiState.preferredAudioLanguage,
                secondaryPreferredAudioLanguage = playerSettingsUiState.secondaryPreferredAudioLanguage,
                preferredSubtitleLanguage = playerSettingsUiState.preferredSubtitleLanguage,
                secondaryPreferredSubtitleLanguage = playerSettingsUiState.secondaryPreferredSubtitleLanguage,
                streamReuseLastLinkEnabled = playerSettingsUiState.streamReuseLastLinkEnabled,
                streamReuseLastLinkCacheHours = playerSettingsUiState.streamReuseLastLinkCacheHours,
                decoderPriority = playerSettingsUiState.decoderPriority,
                mapDV7ToHevc = playerSettingsUiState.mapDV7ToHevc,
                tunnelingEnabled = playerSettingsUiState.tunnelingEnabled,
                useLibass = playerSettingsUiState.useLibass,
                libassRenderType = playerSettingsUiState.libassRenderType,
                rememberLastProfileEnabled = profileSettingsState.rememberLastProfileEnabled,
                selectedTheme = selectedTheme,
                onThemeSelected = ThemeSettingsRepository::setTheme,
                customTheme = customTheme,
                amoledEnabled = amoledEnabled,
                onAmoledToggle = ThemeSettingsRepository::setAmoled,
                liquidGlassNativeTabBarSupported = liquidGlassNativeTabBarSupported,
                liquidGlassNativeTabBarEnabled = liquidGlassNativeTabBarEnabled,
                onLiquidGlassNativeTabBarToggle = ThemeSettingsRepository::setLiquidGlassNativeTabBar,
                desktopNavigationLayout = desktopNavigationLayout,
                onDesktopNavigationLayoutSelected = ThemeSettingsRepository::setDesktopNavigationLayout,
                desktopAppUiScalePercent = desktopAppUiScalePercent,
                onDesktopAppUiScalePercentChange = ThemeSettingsRepository::setDesktopAppUiScalePercent,
                desktopAppUiScaleAppliesToDetails = desktopAppUiScaleAppliesToDetails,
                onDesktopAppUiScaleAppliesToDetailsChange = ThemeSettingsRepository::setDesktopAppUiScaleAppliesToDetails,
                selectedAppLanguage = selectedAppLanguage,
                onAppLanguageSelected = ThemeSettingsRepository::setAppLanguage,
                episodeReleaseNotificationsUiState = episodeReleaseNotificationsUiState,
                tmdbSettings = tmdbSettings,
                mdbListSettings = mdbListSettings,
                qualiCacheSettings = qualiCacheSettings,
                customPosterSettings = customPosterSettings,
                gameLibrarySettings = gameLibrarySettings,
                screensaverSettings = screensaverSettings,
                debridSettings = debridSettings,
                discordPresenceSettings = discordPresenceSettings,
                traktAuthUiState = traktAuthUiState,
                traktCommentsEnabled = traktCommentsEnabled,
                traktSettingsUiState = traktSettingsUiState,
                simklAuthUiState = simklAuthUiState,
                simklSettingsUiState = simklSettingsUiState,
                yamtrackSettingsUiState = yamtrackSettingsUiState,
                lightsSettingsUiState = lightsSettingsUiState,
                homescreenHeroEnabled = homescreenSettingsUiState.heroEnabled,
                homescreenHeroInfoLines = homescreenSettingsUiState.heroInfoLines,
                homescreenHeroInfoPriority = homescreenSettingsUiState.heroInfoPriority,
                homescreenHeroBadgePlacement = homescreenSettingsUiState.heroBadgePlacement,
                homescreenHeroBadgeScale = homescreenSettingsUiState.heroBadgeScale,
                homescreenHeroReleaseStatusUnavailableOnly = homescreenSettingsUiState.heroReleaseStatusUnavailableOnly,
                homescreenHideUnreleasedContent = homescreenSettingsUiState.hideUnreleasedContent,
                homescreenHideWatchedContent = homescreenSettingsUiState.hideWatchedContent,
                homescreenHideCatalogUnderline = homescreenSettingsUiState.hideCatalogUnderline,
                homescreenCatalogRowShuffleEnabled = homescreenSettingsUiState.catalogRowShuffleEnabled,
                homescreenAdaptiveHeroEnabled = homescreenSettingsUiState.adaptiveHeroEnabled,
                homescreenAdaptiveHeroVerticalBias = homescreenSettingsUiState.adaptiveHeroVerticalBias,
                homescreenHeroAmbientBackgroundEnabled = homescreenSettingsUiState.heroAmbientBackgroundEnabled,
                homescreenTvModeEnabled = homescreenSettingsUiState.tvModeEnabled,
                homescreenItems = homescreenSettingsUiState.items,
                randomPlaySettingsUiState = homescreenSettingsUiState,
                metaScreenSettingsUiState = metaScreenSettingsUiState,
                continueWatchingPreferencesUiState = continueWatchingPreferencesUiState,
                posterCardStyleUiState = posterCardStyleUiState,
                onSwitchProfile = onSwitchProfile,
                onHomescreenClick = onHomescreenClick,
                onMetaScreenClick = onMetaScreenClick,
                onContinueWatchingClick = onContinueWatchingClick,
                onAddonsClick = onAddonsClick,
                onPluginsClick = onPluginsClick,
                downloadsUiState = downloadsUiState,
                selectedDownloadsShowId = selectedDownloadsShowId,
                onSelectedDownloadsShowChange = { selectedDownloadsShowId = it },
                onOpenDownload = onOpenDownload,
                onOpenPartyMedia = onOpenPartyMedia,
                onDownloadsDeleteTarget = { pendingDownloadsDelete = it },
                onAccountClick = onAccountClick,
                onSupportersContributorsClick = onSupportersContributorsClick,
                onLicensesAttributionsClick = onLicensesAttributionsClick,
                onCheckForUpdatesClick = onCheckForUpdatesClick,
                onShowLatestChangelogClick = onShowLatestChangelogClick,
                onOpenCollectionEditor = onOpenCollectionEditor,
                onSettingsSearchFocusChange = { settingsSearchHasFocus = it },
            )
        }
        DownloadsSettingsDeleteDialog(
            target = pendingDownloadsDelete,
            onDismiss = { pendingDownloadsDelete = null },
        )
    }
}

@Composable
private fun MobileSettingsScreen(
    page: SettingsPage,
    scrollToTopRequests: Flow<Unit>,
    onPageChange: (SettingsPage) -> Unit,
    showLoadingOverlay: Boolean,
    defaultPlaybackSpeed: Float,
    preferredAudioLanguage: String,
    secondaryPreferredAudioLanguage: String?,
    preferredSubtitleLanguage: String,
    secondaryPreferredSubtitleLanguage: String?,
    streamReuseLastLinkEnabled: Boolean,
    streamReuseLastLinkCacheHours: Int,
    decoderPriority: Int,
    mapDV7ToHevc: Boolean,
    tunnelingEnabled: Boolean,
    useLibass: Boolean,
    libassRenderType: String,
    rememberLastProfileEnabled: Boolean,
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    customTheme: CustomThemeSettings,
    amoledEnabled: Boolean,
    onAmoledToggle: (Boolean) -> Unit,
    liquidGlassNativeTabBarSupported: Boolean,
    liquidGlassNativeTabBarEnabled: Boolean,
    onLiquidGlassNativeTabBarToggle: (Boolean) -> Unit,
    desktopNavigationLayout: DesktopNavigationLayout,
    onDesktopNavigationLayoutSelected: (DesktopNavigationLayout) -> Unit,
    desktopAppUiScalePercent: Int,
    onDesktopAppUiScalePercentChange: (Int) -> Unit,
    desktopAppUiScaleAppliesToDetails: Boolean,
    onDesktopAppUiScaleAppliesToDetailsChange: (Boolean) -> Unit,
    selectedAppLanguage: AppLanguage,
    onAppLanguageSelected: (AppLanguage) -> Unit,
    episodeReleaseNotificationsUiState: EpisodeReleaseNotificationsUiState,
    tmdbSettings: TmdbSettings,
    mdbListSettings: MdbListSettings,
    qualiCacheSettings: QualiCacheSettings,
    customPosterSettings: CustomPosterSettings,
    gameLibrarySettings: GameLibrarySettings,
    screensaverSettings: ScreensaverSettings,
    debridSettings: DebridSettings,
    discordPresenceSettings: DiscordPresenceSettings,
    traktAuthUiState: TraktAuthUiState,
    traktCommentsEnabled: Boolean,
    traktSettingsUiState: TraktSettingsUiState,
    simklAuthUiState: SimklAuthUiState,
    simklSettingsUiState: SimklSettingsUiState,
    yamtrackSettingsUiState: YamtrackSettings,
    lightsSettingsUiState: LightsSettings,
    homescreenHeroEnabled: Boolean,
    homescreenHeroInfoLines: Int,
    homescreenHeroInfoPriority: String,
    homescreenHeroBadgePlacement: HeroBadgePlacement,
    homescreenHeroBadgeScale: Float,
    homescreenHeroReleaseStatusUnavailableOnly: Boolean,
    homescreenHideUnreleasedContent: Boolean,
    homescreenHideWatchedContent: Boolean,
    homescreenHideCatalogUnderline: Boolean,
    homescreenCatalogRowShuffleEnabled: Boolean,
    homescreenAdaptiveHeroEnabled: Boolean,
    homescreenAdaptiveHeroVerticalBias: Float,
    homescreenHeroAmbientBackgroundEnabled: Boolean,
    homescreenTvModeEnabled: Boolean,
    homescreenItems: List<HomeCatalogSettingsItem>,
    randomPlaySettingsUiState: HomeCatalogSettingsUiState,
    metaScreenSettingsUiState: MetaScreenSettingsUiState,
    continueWatchingPreferencesUiState: ContinueWatchingPreferencesUiState,
    posterCardStyleUiState: PosterCardStyleUiState,
    downloadsUiState: DownloadsUiState,
    selectedDownloadsShowId: String?,
    onSelectedDownloadsShowChange: (String?) -> Unit,
    onOpenDownload: (DownloadItem) -> Unit,
    onOpenPartyMedia: (com.nuvio.app.features.party.PartyMedia) -> Unit,
    onDownloadsDeleteTarget: (DownloadsSettingsDeleteTarget) -> Unit,
    onSwitchProfile: (() -> Unit)? = null,
    onHomescreenClick: () -> Unit = {},
    onMetaScreenClick: () -> Unit = {},
    onContinueWatchingClick: () -> Unit = {},
    onAddonsClick: () -> Unit = {},
    onPluginsClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    onSupportersContributorsClick: () -> Unit = {},
    onLicensesAttributionsClick: () -> Unit = {},
    onCheckForUpdatesClick: (() -> Unit)? = null,
    onShowLatestChangelogClick: (() -> Unit)? = null,
    onOpenCollectionEditor: (String?) -> Unit = {},
    onNavigateToHome: (() -> Unit)? = null,
    onNavigateToSearch: (() -> Unit)? = null,
    onNavigateToLibrary: (() -> Unit)? = null,
    onNavigateToDiscover: (() -> Unit)? = null,
    onNavigateToCalendar: (() -> Unit)? = null,
    onNavigateToLiveTv: (() -> Unit)? = null,
    onNavigateToMusic: (() -> Unit)? = null,
    onSettingsSearchFocusChange: (Boolean) -> Unit = {},
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    // Search belongs to the settings screen, not to an individual destination. Keeping this
    // outside the per-page SaveableStateProvider makes the top-bar search one global index.
    var settingsSearchQuery by rememberSaveable { mutableStateOf("") }
    saveableStateHolder.SaveableStateProvider(page.name) {
        val localLibraryUiState by LocalLibraryRepository.uiState.collectAsStateWithLifecycle()
        val localLibraryTitlesState = rememberLocalLibraryTitlesState()

        // Hosted outside the settings list: the browser is a full-screen modal over the page, not
        // a row inside it. The open flag lives in the per-profile session store, so it has to be
        // cleared on the way out or the browser reappears over an unrelated settings page.
        LaunchedEffect(page) {
            if (page != SettingsPage.LocalLibrary) localLibraryTitlesState.browserOpen = false
        }
        ApplyLibraryQuickAccessBrowser(page, localLibraryTitlesState)
        if (page == SettingsPage.LocalLibrary && localLibraryTitlesState.browserOpen) {
            // Opening a title closes the browser first: it is a window-level dialog, so left open
            // it sat over the details page it had just opened.
            val openMetaDetails = LocalOpenMetaDetails.current
            CompositionLocalProvider(
                LocalOpenMetaDetails provides openMetaDetails?.let { open ->
                    { type: String, id: String ->
                        localLibraryTitlesState.browserOpen = false
                        open(type, id)
                    }
                },
            ) {
                LocalLibraryBrowserDialog(
                    state = localLibraryUiState,
                    titlesState = localLibraryTitlesState,
                    onDismiss = { localLibraryTitlesState.browserOpen = false },
                )
            }
        }
        var rootSearchVisible by rememberSaveable { mutableStateOf(isDesktop) }
        var rootSearchRevealAnimating by rememberSaveable { mutableStateOf(false) }
        val listState = rememberLazyListState()
        val hapticFeedback = LocalHapticFeedback.current
        val hapticScope = rememberCoroutineScope()
        val rootSearchRevealConnection = rememberSettingsRootSearchRevealConnection(
            page = page,
            listState = listState,
            query = settingsSearchQuery,
            searchVisible = rootSearchVisible,
        ) {
            rootSearchVisible = true
            rootSearchRevealAnimating = true
            hapticScope.launch {
                delay(SettingsSearchRevealHapticDelayMillis)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        val searchEntries = rememberSettingsSearchEntries(
            pluginsEnabled = AppFeaturePolicy.pluginsEnabled,
            downloadsEnabled = AppFeaturePolicy.downloadsEnabled,
            notificationsEnabled = AppFeaturePolicy.notificationsEnabled,
            liquidGlassNativeTabBarSupported = liquidGlassNativeTabBarSupported,
            switchProfileAvailable = onSwitchProfile != null,
            checkForUpdatesAvailable = onCheckForUpdatesClick != null,
            languageCode = selectedAppLanguage.code,
        )

        val searchFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
        fun openSearchTarget(target: SettingsSearchTarget) {
            SettingsSearchHistoryRepository.record(
                settingsSearchQuery,
                (target as? SettingsSearchTarget.Page)?.page,
            )
            // Remove the results layer before mounting the destination. Otherwise it can keep
            // covering the page whose row is waiting to consume the requested scroll anchor.
            settingsSearchQuery = ""
            searchFocusManager.clearFocus()
            when (target) {
                is SettingsSearchTarget.Page -> {
                    target.anchor?.let { anchor ->
                        SettingsScrollAnchor.request(
                            anchor = anchor,
                            fallbackAnchor = target.fallbackAnchor,
                            fallbackTitle = target.fallbackTitle,
                        )
                    }
                    when (target.page) {
                        SettingsPage.Account -> onAccountClick()
                        SettingsPage.SupportersContributors -> onSupportersContributorsClick()
                        SettingsPage.LicensesAttributions -> onLicensesAttributionsClick()
                        SettingsPage.ContinueWatching -> onContinueWatchingClick()
                        SettingsPage.Addons -> onAddonsClick()
                        SettingsPage.Plugins -> {
                            if (AppFeaturePolicy.pluginsEnabled) {
                                onPluginsClick()
                            }
                        }
                        SettingsPage.Homescreen -> onHomescreenClick()
                        SettingsPage.MetaScreen -> onMetaScreenClick()
                        else -> onPageChange(target.page)
                    }
                }
                SettingsSearchTarget.SwitchProfile -> onSwitchProfile?.invoke()
                SettingsSearchTarget.CheckForUpdates -> onCheckForUpdatesClick?.invoke()
            }
        }

        LaunchedEffect(rootSearchRevealAnimating) {
            if (rootSearchRevealAnimating) {
                delay(SettingsSearchRevealAnimationMillis)
                rootSearchRevealAnimating = false
            }
        }

        LaunchedEffect(scrollToTopRequests) {
            scrollToTopRequests.collect {
                listState.animateScrollToItem(0)
            }
        }

        NuvioScreen(
            modifier = Modifier.nestedScroll(rootSearchRevealConnection),
            listState = listState,
        ) {
            stickyHeader {
                val previousPage = page.desktopBackPage()
                NuvioScreenHeader(
                    title = stringResource(page.titleRes),
                    onBack = previousPage?.let { default ->
                        {
                            val dest = SettingsScrollAnchor.consumeBackTo() ?: default
                            onPageChange(dest)
                        }
                    },
                )
            }

            when (page) {
                SettingsPage.Root -> {
                    settingsSearchRootContent(
                        query = settingsSearchQuery,
                        entries = searchEntries,
                        isTablet = false,
                        showSearchField = rootSearchVisible,
                        animateSearchField = rootSearchRevealAnimating,
                        onQueryChange = { settingsSearchQuery = it },
                        onSearchFocusChange = onSettingsSearchFocusChange,
                        onTargetClick = { openSearchTarget(it) },
                    )
                    if (settingsSearchQuery.isBlank()) {
                        settingsRootContent(
                            isTablet = false,
                            onPlaybackClick = { onPageChange(SettingsPage.Playback) },
                            onPlayerClick = { onPageChange(SettingsPage.Player) },
                            onRandomPlayClick = { onPageChange(SettingsPage.RandomPlay) },
                            onDiscoverClick = { onPageChange(SettingsPage.Discover) },
                            onStreamsClick = { onPageChange(SettingsPage.Streams) },
                            onLocalLibraryClick = { onPageChange(SettingsPage.LocalLibrary) },
                            onGamesClick = { onPageChange(SettingsPage.Games) },
                            onIptvClick = { onPageChange(SettingsPage.Iptv) },
                            onAutoDownloadsClick = { onPageChange(SettingsPage.AutoDownloads) },
                            onAppearanceClick = { onPageChange(SettingsPage.Appearance) },
                            onAdvancedClick = { onPageChange(SettingsPage.Advanced) },
                            onNotificationsClick = { onPageChange(SettingsPage.Notifications) },
                            onContinueWatchingClick = { onPageChange(SettingsPage.ContinueWatching) },
                            onAddonsClick = { onPageChange(SettingsPage.Addons) },
                            onPluginsClick = { onPageChange(SettingsPage.Plugins) },
                            onHomescreenClick = { onPageChange(SettingsPage.Homescreen) },
                            onMetaScreenClick = { onPageChange(SettingsPage.MetaScreen) },
                            onCollectionsClick = { onPageChange(SettingsPage.Collections) },
                            onIntegrationsClick = { onPageChange(SettingsPage.Integrations) },
                            onTraktClick = { onPageChange(SettingsPage.TraktAuthentication) },
                            onSimklClick = { onPageChange(SettingsPage.SimklAuthentication) },
                            onYamtrackClick = { onPageChange(SettingsPage.YamtrackAuthentication) },
                            onSupportersContributorsClick = onSupportersContributorsClick,
                            onLicensesAttributionsClick = onLicensesAttributionsClick,
                            onCheckForUpdatesClick = onCheckForUpdatesClick,
                            onDownloadsClick = { onPageChange(SettingsPage.Downloads) },
                            onAccountClick = onAccountClick,
                            onSwitchProfileClick = onSwitchProfile,
                            showDownloadsEntry = AppFeaturePolicy.downloadsEnabled,
                            showNotificationsEntry = AppFeaturePolicy.notificationsEnabled,
                            showPluginsEntry = AppFeaturePolicy.pluginsEnabled,
                        )
                    }
                }
                SettingsPage.Account -> accountSettingsContent(
                    isTablet = false,
                    rememberLastProfileEnabled = rememberLastProfileEnabled,
                )
                SettingsPage.SupportersContributors -> supportersContributorsContent(
                    isTablet = false,
                )
                SettingsPage.LicensesAttributions -> licensesAttributionsContent(
                    isTablet = false,
                )
                SettingsPage.Playback -> playbackSettingsContent(
                    isTablet = false,
                    showLoadingOverlay = showLoadingOverlay,
                    defaultPlaybackSpeed = defaultPlaybackSpeed,
                    preferredAudioLanguage = preferredAudioLanguage,
                    secondaryPreferredAudioLanguage = secondaryPreferredAudioLanguage,
                    preferredSubtitleLanguage = preferredSubtitleLanguage,
                    secondaryPreferredSubtitleLanguage = secondaryPreferredSubtitleLanguage,
                    streamReuseLastLinkEnabled = streamReuseLastLinkEnabled,
                    streamReuseLastLinkCacheHours = streamReuseLastLinkCacheHours,
                    decoderPriority = decoderPriority,
                    mapDV7ToHevc = mapDV7ToHevc,
                    tunnelingEnabled = tunnelingEnabled,
                    useLibass = useLibass,
                    libassRenderType = libassRenderType,
                    group = PlaybackSettingsGroup.VideoAudio,
                )
                SettingsPage.Player -> playbackSettingsContent(
                    isTablet = false,
                    showLoadingOverlay = showLoadingOverlay,
                    defaultPlaybackSpeed = defaultPlaybackSpeed,
                    preferredAudioLanguage = preferredAudioLanguage,
                    secondaryPreferredAudioLanguage = secondaryPreferredAudioLanguage,
                    preferredSubtitleLanguage = preferredSubtitleLanguage,
                    secondaryPreferredSubtitleLanguage = secondaryPreferredSubtitleLanguage,
                    streamReuseLastLinkEnabled = streamReuseLastLinkEnabled,
                    streamReuseLastLinkCacheHours = streamReuseLastLinkCacheHours,
                    decoderPriority = decoderPriority,
                    mapDV7ToHevc = mapDV7ToHevc,
                    tunnelingEnabled = tunnelingEnabled,
                    useLibass = useLibass,
                    libassRenderType = libassRenderType,
                    group = PlaybackSettingsGroup.Player,
                )
                SettingsPage.Discover -> discoverSettingsContent(
                    isTablet = false,
                    settings = randomPlaySettingsUiState,
                )
                SettingsPage.RandomPlay -> randomPlaySettingsContent(
                    isTablet = false,
                    settings = randomPlaySettingsUiState,
                )
                SettingsPage.Streams -> streamsSettingsContent(
                    isTablet = false,
                    onOpenStreamScoring = { onPageChange(SettingsPage.StreamScoring) },
                )
                SettingsPage.LocalLibrary -> localLibraryContent(
                    isTablet = false,
                    state = localLibraryUiState,
                    titlesState = localLibraryTitlesState,
                )
                SettingsPage.AutoDownloads -> libraryDownloadsSection(
                    isTablet = false,
                    onDownloadsClick = { onPageChange(SettingsPage.Downloads) },
                )
                SettingsPage.Downloads -> downloadsSettingsContent(
                    isTablet = false,
                    uiState = downloadsUiState,
                    selectedShowId = selectedDownloadsShowId,
                    onSelectedShowChange = onSelectedDownloadsShowChange,
                    onOpenDownload = onOpenDownload,
                    onDeleteTarget = onDownloadsDeleteTarget,
                )
                SettingsPage.WatchParty -> watchPartySettingsContent(onOpenPartyMedia)
                SettingsPage.StreamScoring -> streamScoringSection(isTablet = false)
                SettingsPage.KeyboardShortcuts -> keyboardShortcutsContent(
                    isTablet = false,
                )
                SettingsPage.Appearance -> appearanceSettingsContent(
                    isTablet = false,
                    selectedTheme = selectedTheme,
                    onThemeSelected = onThemeSelected,
                    customTheme = customTheme,
                    liquidGlassNativeTabBarSupported = liquidGlassNativeTabBarSupported,
                    liquidGlassNativeTabBarEnabled = liquidGlassNativeTabBarEnabled,
                    onLiquidGlassNativeTabBarToggle = onLiquidGlassNativeTabBarToggle,
                    desktopNavigationLayout = desktopNavigationLayout,
                    onDesktopNavigationLayoutSelected = onDesktopNavigationLayoutSelected,
                    desktopAppUiScalePercent = desktopAppUiScalePercent,
                    onDesktopAppUiScalePercentChange = onDesktopAppUiScalePercentChange,
                    desktopAppUiScaleAppliesToDetails = desktopAppUiScaleAppliesToDetails,
                    onDesktopAppUiScaleAppliesToDetailsChange = onDesktopAppUiScaleAppliesToDetailsChange,
                    selectedAppLanguage = selectedAppLanguage,
                    onAppLanguageSelected = onAppLanguageSelected,
                    posterCardStyleUiState = posterCardStyleUiState,
                )
                SettingsPage.Advanced -> advancedSettingsContent(
                    isTablet = false,
                    rememberLastProfileEnabled = rememberLastProfileEnabled,
                )
                SettingsPage.Notifications -> if (AppFeaturePolicy.notificationsEnabled) {
                    notificationsSettingsContent(
                        isTablet = false,
                        uiState = episodeReleaseNotificationsUiState,
                    )
                }
                SettingsPage.ContinueWatching -> continueWatchingSettingsContent(
                    isTablet = false,
                    isVisible = continueWatchingPreferencesUiState.isVisible,
                    style = continueWatchingPreferencesUiState.style,
                    clickAction = continueWatchingPreferencesUiState.clickAction,
                    upNextFromFurthestEpisode = continueWatchingPreferencesUiState.upNextFromFurthestEpisode,
                    useEpisodeThumbnails = continueWatchingPreferencesUiState.useEpisodeThumbnails,
                    showUnairedNextUp = continueWatchingPreferencesUiState.showUnairedNextUp,
                    separateNextUpRow = continueWatchingPreferencesUiState.separateNextUpRow,
                    separateUpcomingRow = continueWatchingPreferencesUiState.separateUpcomingRow,
                    blurNextUp = continueWatchingPreferencesUiState.blurNextUp,
                    showResumePromptOnLaunch = continueWatchingPreferencesUiState.showResumePromptOnLaunch,
                    sortMode = continueWatchingPreferencesUiState.sortMode,
                    relativeAirCountdown = continueWatchingPreferencesUiState.relativeAirCountdown,
                )
                SettingsPage.ContentDiscovery -> contentDiscoveryContent(
                    isTablet = false,
                    showPluginsEntry = AppFeaturePolicy.pluginsEnabled,
                    showDownloadsEntry = AppFeaturePolicy.downloadsEnabled,
                    onAddonsClick = onAddonsClick,
                    onPluginsClick = onPluginsClick,
                    onHomescreenClick = onHomescreenClick,
                    onMetaScreenClick = onMetaScreenClick,
                    onCollectionsClick = { onPageChange(SettingsPage.Collections) },
                    onDownloadsClick = { onPageChange(SettingsPage.Downloads) },
                )
                SettingsPage.Collections -> collectionsSettingsContent(
                    isTablet = false,
                    onNavigateToEditor = onOpenCollectionEditor,
                )
                SettingsPage.Addons -> addonsSettingsContent()
                SettingsPage.Plugins -> if (AppFeaturePolicy.pluginsEnabled) pluginsSettingsContent() else addonsSettingsContent()
                SettingsPage.Homescreen -> homescreenSettingsContent(
                    isTablet = false,
                    heroEnabled = homescreenHeroEnabled,
                    heroInfoLines = homescreenHeroInfoLines,
                    heroInfoPriority = homescreenHeroInfoPriority,
                    heroBadgePlacement = homescreenHeroBadgePlacement,
                    heroBadgeScale = homescreenHeroBadgeScale,
                    heroReleaseStatusUnavailableOnly = homescreenHeroReleaseStatusUnavailableOnly,
                    hideUnreleasedContent = homescreenHideUnreleasedContent,
                    hideWatchedContent = homescreenHideWatchedContent,
                    hideCatalogUnderline = homescreenHideCatalogUnderline,
                    catalogRowShuffleEnabled = homescreenCatalogRowShuffleEnabled,
                    adaptiveHeroEnabled = homescreenAdaptiveHeroEnabled,
                    adaptiveHeroVerticalBias = homescreenAdaptiveHeroVerticalBias,
                    heroAmbientBackgroundEnabled = homescreenHeroAmbientBackgroundEnabled,
                    tvModeEnabled = homescreenTvModeEnabled,
                    items = homescreenItems,
                )
                SettingsPage.MetaScreen -> metaScreenSettingsContent(
                    isTablet = false,
                    uiState = metaScreenSettingsUiState,
                )
                SettingsPage.Integrations -> integrationsContent(
                    isTablet = false,
                    onTmdbClick = { onPageChange(SettingsPage.TmdbEnrichment) },
                    onMdbListClick = { onPageChange(SettingsPage.MdbListRatings) },
                    onQualiCacheClick = { onPageChange(SettingsPage.QualiCache) },
                    onPosterServiceClick = { onPageChange(SettingsPage.PosterService) },
                    onDebridClick = { onPageChange(SettingsPage.Debrid) },
                    onTraktClick = { onPageChange(SettingsPage.TraktAuthentication) },
                    onSimklClick = { onPageChange(SettingsPage.SimklAuthentication) },
                    onYamtrackClick = { onPageChange(SettingsPage.YamtrackAuthentication) },
                    onYouTubeAccountClick = { onPageChange(SettingsPage.YouTubeAccount) },
                    onSeekrClick = { onPageChange(SettingsPage.Seekr) },
                    onLightsClick = { onPageChange(SettingsPage.Lights) },
                    onDiscordClick = { onPageChange(SettingsPage.DiscordPresence) },
                )
                SettingsPage.TmdbEnrichment -> tmdbSettingsContent(
                    isTablet = false,
                    settings = tmdbSettings,
                )
                SettingsPage.MdbListRatings -> mdbListSettingsContent(
                    isTablet = false,
                    settings = mdbListSettings,
                )
                SettingsPage.QualiCache -> qualiCacheSettingsContent(
                    isTablet = false,
                    settings = qualiCacheSettings,
                )
                SettingsPage.PosterService -> customPosterSettingsContent(
                    isTablet = false,
                    settings = customPosterSettings,
                )
                SettingsPage.Games -> gamesSettingsContent(
                    isTablet = false,
                    settings = gameLibrarySettings,
                )
                SettingsPage.Iptv -> iptvSettingsContent(isTablet = false)
                SettingsPage.Screensaver -> screensaverSettingsContent(
                    isTablet = false,
                    settings = screensaverSettings,
                )
                SettingsPage.VoiceControl -> voiceControlSettingsContent(isTablet = false) // fork:voice-control
                SettingsPage.Preshow -> preshowSettingsContent(isTablet = false)
                SettingsPage.Music -> musicSettingsContent(isTablet = false) // fork:music
                SettingsPage.Debrid -> debridSettingsContent(
                    isTablet = false,
                    settings = debridSettings,
                )
                SettingsPage.TraktAuthentication -> traktSettingsContent(
                    isTablet = false,
                    uiState = traktAuthUiState,
                    settingsUiState = traktSettingsUiState,
                    commentsEnabled = traktCommentsEnabled,
                    onCommentsEnabledChange = TraktCommentsSettings::setEnabled,
                )
                SettingsPage.SimklAuthentication -> simklSettingsContent(isTablet = false, uiState = simklAuthUiState, settingsUiState = simklSettingsUiState)
                SettingsPage.YamtrackAuthentication -> yamtrackSettingsContent(isTablet = false, settings = yamtrackSettingsUiState)
                SettingsPage.YouTubeAccount -> youTubeAccountSettingsContent(isTablet = false)
                SettingsPage.Seekr -> seekrSettingsContent(isTablet = false)
                SettingsPage.Lights -> lightsSettingsContent(isTablet = false, settings = lightsSettingsUiState)
                SettingsPage.DiscordPresence -> discordPresenceSettingsContent(
                    isTablet = false,
                    settings = discordPresenceSettings,
                    onModeChange = DiscordPresenceSettingsRepository::setMode,
                    onEpisodeArtworkChange = DiscordPresenceSettingsRepository::setEpisodeArtwork,
                    onActivityStyleChange = DiscordPresenceSettingsRepository::setActivityStyle,
                    onActivityNameChange = DiscordPresenceSettingsRepository::setActivityName,
                )
            }
        }
    }
}

/**
 * The Library nav item's right-click shortcuts: once the LocalLibrary page is up (App has already
 * requested it), open the titles browser or make sure it isn't covering the settings. Both the
 * mobile and tablet/desktop layouts host the browser, so both call this.
 */
@Composable
private fun ApplyLibraryQuickAccessBrowser(page: SettingsPage, titlesState: LocalLibraryTitlesState) {
    LaunchedEffect(page, LibraryQuickAccess.browserPending) {
        val openBrowser = LibraryQuickAccess.browserPending ?: return@LaunchedEffect
        if (page != SettingsPage.LocalLibrary) return@LaunchedEffect
        LibraryQuickAccess.browserPending = null
        if (openBrowser) {
            titlesState.openBrowser(FILTER_ALL)
        } else {
            titlesState.browserOpen = false
        }
    }
}

private fun SettingsPage.isEnabledByFeaturePolicy(): Boolean =
    when (this) {
        SettingsPage.Notifications -> AppFeaturePolicy.notificationsEnabled
        SettingsPage.Plugins -> AppFeaturePolicy.pluginsEnabled
        SettingsPage.AutoDownloads,
        SettingsPage.Downloads -> AppFeaturePolicy.downloadsEnabled
        else -> true
    }

@Composable
private fun rememberSettingsRootSearchRevealConnection(
    page: SettingsPage,
    listState: LazyListState,
    query: String,
    searchVisible: Boolean,
    onReveal: () -> Unit,
): NestedScrollConnection {
    val revealThresholdPx = with(LocalDensity.current) { SettingsSearchRevealThreshold.toPx() }
    val currentOnReveal by rememberUpdatedState(onReveal)
    var pullDistancePx by remember(page) { mutableStateOf(0f) }
    var revealTriggered by remember(page) { mutableStateOf(false) }

    return remember(page, listState, query, searchVisible, revealThresholdPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val isRootAtTop = page == SettingsPage.Root &&
                    listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                val canRevealSearch = isRootAtTop && !searchVisible && !revealTriggered && query.isBlank()

                if (canRevealSearch && available.y > 0f) {
                    pullDistancePx += available.y
                    if (pullDistancePx >= revealThresholdPx) {
                        pullDistancePx = 0f
                        revealTriggered = true
                        currentOnReveal()
                    }
                } else if (!isRootAtTop || available.y < 0f) {
                    pullDistancePx = 0f
                }

                return Offset.Zero
            }
        }
    }
}

@Composable
private fun TabletSettingsScreen(
    page: SettingsPage,
    entryRequester: FocusRequester?,
    scrollToTopRequests: Flow<Unit>,
    onPageChange: (SettingsPage) -> Unit,
    showContextPanel: Boolean,
    showLoadingOverlay: Boolean,
    defaultPlaybackSpeed: Float,
    preferredAudioLanguage: String,
    secondaryPreferredAudioLanguage: String?,
    preferredSubtitleLanguage: String,
    secondaryPreferredSubtitleLanguage: String?,
    streamReuseLastLinkEnabled: Boolean,
    streamReuseLastLinkCacheHours: Int,
    decoderPriority: Int,
    mapDV7ToHevc: Boolean,
    tunnelingEnabled: Boolean,
    useLibass: Boolean,
    libassRenderType: String,
    rememberLastProfileEnabled: Boolean,
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    customTheme: CustomThemeSettings,
    amoledEnabled: Boolean,
    onAmoledToggle: (Boolean) -> Unit,
    liquidGlassNativeTabBarSupported: Boolean,
    liquidGlassNativeTabBarEnabled: Boolean,
    onLiquidGlassNativeTabBarToggle: (Boolean) -> Unit,
    desktopNavigationLayout: DesktopNavigationLayout,
    onDesktopNavigationLayoutSelected: (DesktopNavigationLayout) -> Unit,
    desktopAppUiScalePercent: Int,
    onDesktopAppUiScalePercentChange: (Int) -> Unit,
    desktopAppUiScaleAppliesToDetails: Boolean,
    onDesktopAppUiScaleAppliesToDetailsChange: (Boolean) -> Unit,
    desktopColumnGuidesVisible: Boolean,
    onDesktopColumnGuidesVisibleChange: (Boolean) -> Unit,
    desktopSettingsFullWidth: Boolean,
    onDesktopSettingsFullWidthChange: (Boolean) -> Unit,
    selectedAppLanguage: AppLanguage,
    onAppLanguageSelected: (AppLanguage) -> Unit,
    episodeReleaseNotificationsUiState: EpisodeReleaseNotificationsUiState,
    tmdbSettings: TmdbSettings,
    mdbListSettings: MdbListSettings,
    qualiCacheSettings: QualiCacheSettings,
    customPosterSettings: CustomPosterSettings,
    gameLibrarySettings: GameLibrarySettings,
    screensaverSettings: ScreensaverSettings,
    debridSettings: DebridSettings,
    discordPresenceSettings: DiscordPresenceSettings,
    traktAuthUiState: TraktAuthUiState,
    traktCommentsEnabled: Boolean,
    traktSettingsUiState: TraktSettingsUiState,
    simklAuthUiState: SimklAuthUiState,
    simklSettingsUiState: SimklSettingsUiState,
    yamtrackSettingsUiState: YamtrackSettings,
    lightsSettingsUiState: LightsSettings,
    homescreenHeroEnabled: Boolean,
    homescreenHeroInfoLines: Int,
    homescreenHeroInfoPriority: String,
    homescreenHeroBadgePlacement: HeroBadgePlacement,
    homescreenHeroBadgeScale: Float,
    homescreenHeroReleaseStatusUnavailableOnly: Boolean,
    homescreenHideUnreleasedContent: Boolean,
    homescreenHideWatchedContent: Boolean,
    homescreenHideCatalogUnderline: Boolean,
    homescreenCatalogRowShuffleEnabled: Boolean,
    homescreenAdaptiveHeroEnabled: Boolean,
    homescreenAdaptiveHeroVerticalBias: Float,
    homescreenHeroAmbientBackgroundEnabled: Boolean,
    homescreenTvModeEnabled: Boolean,
    homescreenItems: List<HomeCatalogSettingsItem>,
    randomPlaySettingsUiState: HomeCatalogSettingsUiState,
    metaScreenSettingsUiState: MetaScreenSettingsUiState,
    continueWatchingPreferencesUiState: ContinueWatchingPreferencesUiState,
    posterCardStyleUiState: PosterCardStyleUiState,
    downloadsUiState: DownloadsUiState,
    selectedDownloadsShowId: String?,
    onSelectedDownloadsShowChange: (String?) -> Unit,
    onOpenDownload: (DownloadItem) -> Unit,
    onOpenPartyMedia: (com.nuvio.app.features.party.PartyMedia) -> Unit,
    onDownloadsDeleteTarget: (DownloadsSettingsDeleteTarget) -> Unit,
    profileAvatars: List<AvatarCatalogItem>,
    onSwitchProfile: (() -> Unit)? = null,
    onSupportersContributorsClick: () -> Unit = {},
    onLicensesAttributionsClick: () -> Unit = {},
    onCheckForUpdatesClick: (() -> Unit)? = null,
    onShowLatestChangelogClick: (() -> Unit)? = null,
    onOpenCollectionEditor: (String?) -> Unit = {},
    onNavigateToHome: (() -> Unit)? = null,
    onNavigateToSearch: (() -> Unit)? = null,
    onNavigateToLibrary: (() -> Unit)? = null,
    onNavigateToDiscover: (() -> Unit)? = null,
    onNavigateToCalendar: (() -> Unit)? = null,
    onNavigateToLiveTv: (() -> Unit)? = null,
    onNavigateToMusic: (() -> Unit)? = null,
    onSettingsSearchFocusChange: (Boolean) -> Unit = {},
) {
    val tokens = MaterialTheme.nuvio
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topOffset = max(statusBarPadding + 18.dp, 36.dp)

    // A single desktop top bar must also have a single query when the selected settings page
    // changes. Per-page query state caused result clicks to reopen stale result lists.
    var settingsSearchQuery by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // Any page change closes the search: results replace the page in the list, so picking a
    // category with a query still typed would otherwise leave the results showing over it.
    fun closeSettingsSearch() {
        if (settingsSearchQuery.isEmpty()) return
        settingsSearchQuery = ""
        focusManager.clearFocus()
    }

    fun openInlinePage(page: SettingsPage) {
        closeSettingsSearch()
        onPageChange(page)
    }

    val saveableStateHolder = rememberSaveableStateHolder()
    val activeSidebarPage = remember(page) { page.desktopSidebarPage() }
    val profileState by remember { ProfileRepository.state }.collectAsStateWithLifecycle()

    // SaveableStateProvider(page.name) rebuilds the whole page subtree when the page changes, so the
    // row that took the Select — or whatever else held the keys — is disposed with it and nothing is
    // focused afterwards. Hand the keys back once the new page has composed: the first content row
    // for a page that was opened, the category row for a sub-page that Back folded back into it.
    var pageBeforeChange by remember { mutableStateOf(page) }
    val contentEntryRequester = remember { FocusRequester() }
    var contentEntryActive by remember { mutableStateOf(false) }
    LaunchedEffect(page) {
        val from = pageBeforeChange
        if (from == page) return@LaunchedEffect
        pageBeforeChange = page
        // A frame for the new subtree to compose and attach its focus requesters.
        withFrameNanos { }

        suspend fun focusSidebarEntry(): Boolean {
            var focused = false
            var attempts = 0
            while (!focused && attempts < 3) {
                focused = SettingsSidebarFocus.requestEntry(focusManager) ||
                    (entryRequester != null && runCatching { entryRequester.requestFocus() }.getOrDefault(false))
                if (!focused) withFrameNanos { }
                attempts++
            }
            return focused
        }

        // Back from a sub-page belongs on the category list it came from.
        if (from.desktopBackPage() == page) {
            focusSidebarEntry()
            SettingsFocusDiagnostics.log("page-change back-to-parent $from -> $page: sidebar entry")
            return@LaunchedEffect
        }

        // A page that was opened takes the keys in its content. The controls register themselves as
        // entry points, so focus lands on the first real row rather than on a proxy. If the page has
        // no registered control yet (a text field first, say), geometry gets the keys into the
        // column; the 1dp stop remains as the last resort before giving up and leaving the keys in
        // the category list.
        var contentFocused = SettingsContentFocus.requestEntry(focusManager)
        if (!contentFocused) {
            contentFocused = focusManager.moveFocus(FocusDirection.Right)
        }
        if (!contentFocused) {
            contentEntryActive = true
            try {
                withFrameNanos { }
                val entered = runCatching { contentEntryRequester.requestFocus() }.getOrDefault(false)
                withFrameNanos { }
                contentFocused = entered && focusManager.moveFocus(FocusDirection.Down)
            } finally {
                contentEntryActive = false
            }
        }
        // A page with nothing focusable: leave the keys on the category list instead.
        if (!contentFocused) focusSidebarEntry()
        SettingsFocusDiagnostics.log("page-change $from -> $page: contentFocused=$contentFocused")
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .settingsSkyBackground(tokens.colors.background, tokens.colors.accent),
        contentAlignment = Alignment.TopCenter,
    ) {
        val contextPanelWidth = if (showContextPanel) DesktopSettingsContextPanelWidth else 0.dp
        val desiredShellWidth = DesktopSettingsSidebarWidth + DesktopSettingsMainColumnWidth + contextPanelWidth
        // Full width: the shell takes the whole window and the main column absorbs the slack;
        // otherwise the fixed column set sits centred with the window's spare width as gutters.
        val shellWidth = if (desktopSettingsFullWidth || maxWidth < desiredShellWidth) maxWidth else desiredShellWidth
        val contentWidth = shellWidth - DesktopSettingsSidebarWidth

        // Constant key: the provider's identity must survive category changes. Keying on page.name
        // disposed this subtree (and the focused control) on every page change, so focus went null
        // and keys stopped working. The page is still read below, so content recomposes.
        saveableStateHolder.SaveableStateProvider("settings-page") {
            val localLibraryUiState by LocalLibraryRepository.uiState.collectAsStateWithLifecycle()
            val localLibraryTitlesState = rememberLocalLibraryTitlesState()

            // Hosted outside the settings list: the browser is a full-screen modal over the page, not
            // a row inside it. The open flag lives in the per-profile session store, so it has to be
            // cleared on the way out or the browser reappears over an unrelated settings page.
            LaunchedEffect(page) {
                if (page != SettingsPage.LocalLibrary) localLibraryTitlesState.browserOpen = false
            }
            ApplyLibraryQuickAccessBrowser(page, localLibraryTitlesState)
            if (page == SettingsPage.LocalLibrary && localLibraryTitlesState.browserOpen) {
                // Opening a title closes the browser first: it is a window-level dialog, so left open
                // it sat over the details page it had just opened.
                val openMetaDetails = LocalOpenMetaDetails.current
                CompositionLocalProvider(
                    LocalOpenMetaDetails provides openMetaDetails?.let { open ->
                        { type: String, id: String ->
                            localLibraryTitlesState.browserOpen = false
                            open(type, id)
                        }
                    },
                ) {
                    LocalLibraryBrowserDialog(
                        state = localLibraryUiState,
                        titlesState = localLibraryTitlesState,
                        onDismiss = { localLibraryTitlesState.browserOpen = false },
                    )
                }
            }
            var rootSearchVisible by rememberSaveable { mutableStateOf(false) }
            var rootSearchRevealAnimating by rememberSaveable { mutableStateOf(false) }
            val hapticFeedback = LocalHapticFeedback.current
            val hapticScope = rememberCoroutineScope()
            val searchEntries = rememberSettingsSearchEntries(
                pluginsEnabled = AppFeaturePolicy.pluginsEnabled,
                downloadsEnabled = AppFeaturePolicy.downloadsEnabled,
                notificationsEnabled = AppFeaturePolicy.notificationsEnabled,
                liquidGlassNativeTabBarSupported = liquidGlassNativeTabBarSupported,
                switchProfileAvailable = onSwitchProfile != null,
                checkForUpdatesAvailable = onCheckForUpdatesClick != null,
                languageCode = selectedAppLanguage.code,
            )

            val searchFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
            fun openSearchTarget(target: SettingsSearchTarget) {
                SettingsSearchHistoryRepository.record(
                    settingsSearchQuery,
                    (target as? SettingsSearchTarget.Page)?.page,
                )
                settingsSearchQuery = ""
                // The field is in the top bar, outside the page. Without this it kept the keys,
                // so the arrows edited text instead of moving through the page the result opened.
                searchFocusManager.clearFocus()
                when (target) {
                    is SettingsSearchTarget.Page -> {
                        if (target.page.isEnabledByFeaturePolicy()) {
                            target.anchor?.let { anchor ->
                                SettingsScrollAnchor.request(
                                    anchor = anchor,
                                    fallbackAnchor = target.fallbackAnchor,
                                    fallbackTitle = target.fallbackTitle,
                                )
                            }
                            openInlinePage(target.page)
                        }
                    }
                    SettingsSearchTarget.SwitchProfile -> onSwitchProfile?.invoke()
                    SettingsSearchTarget.CheckForUpdates -> onCheckForUpdatesClick?.invoke()
                }
            }

            val listState = rememberLazyListState()
            val bottomOverlayPadding = LocalNuvioBottomNavigationOverlayPadding.current
            val rootSearchRevealConnection = rememberSettingsRootSearchRevealConnection(
                page = page,
                listState = listState,
                query = settingsSearchQuery,
                searchVisible = rootSearchVisible,
            ) {
                rootSearchVisible = true
                rootSearchRevealAnimating = true
                hapticScope.launch {
                    delay(SettingsSearchRevealAnimationMillis)
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            LaunchedEffect(rootSearchRevealAnimating) {
                if (rootSearchRevealAnimating) {
                    delay(SettingsSearchRevealAnimationMillis)
                    rootSearchRevealAnimating = false
                }
            }
            LaunchedEffect(scrollToTopRequests) {
                scrollToTopRequests.collect {
                    listState.animateScrollToItem(0)
                }
            }
            SeekSettingsAnchorIntoView(listState)

            // The content's Down/Up fallback: when focus is on the last composed row, an arrow has
            // nowhere to go until the list reveals more. Page the list and the item retries.
            DisposableEffect(listState) {
                SettingsContentScroll.register { direction ->
                    val viewport = listState.layoutInfo.viewportSize.height
                    if (viewport > 0f) {
                        hapticScope.launch { listState.animateScrollBy(viewport * 0.85f * direction) }
                    }
                }
                onDispose { SettingsContentScroll.unregister() }
            }

        val columnGuideBrush = settingsColumnGuideBrush()
        val columnGuideModifier = if (desktopColumnGuidesVisible) {
            Modifier.drawBehind {
                    val strokeWidth = SettingsColumnGuideWidth.toPx()
                    drawLine(
                        brush = columnGuideBrush,
                        start = Offset(strokeWidth / 2f, 0f),
                        end = Offset(strokeWidth / 2f, size.height),
                        strokeWidth = strokeWidth,
                    )
                    drawLine(
                        brush = columnGuideBrush,
                        start = Offset(size.width - strokeWidth / 2f, 0f),
                        end = Offset(size.width - strokeWidth / 2f, size.height),
                        strokeWidth = strokeWidth,
                    )
                }
        } else {
            Modifier
        }

        Column(
            modifier = Modifier
                .width(shellWidth)
                .fillMaxHeight()
                .then(columnGuideModifier),
        ) {
            DesktopSettingsTopBar(
                query = settingsSearchQuery,
                activeProfile = profileState.activeProfile,
                profileAvatars = profileAvatars,
                onQueryChange = { settingsSearchQuery = it },
                onSearchFocusChange = onSettingsSearchFocusChange,
                // Account, not the profile selector: it is the one way into Account from the top bar,
                // and the selector is the first row there.
                onProfileClick = { openInlinePage(SettingsPage.Account) },
                onShowLatestChangelogClick = onShowLatestChangelogClick ?: onCheckForUpdatesClick,
                columnGuidesVisible = desktopColumnGuidesVisible,
                onColumnGuidesVisibleChange = onDesktopColumnGuidesVisibleChange,
                fullWidth = desktopSettingsFullWidth,
                onFullWidthChange = onDesktopSettingsFullWidthChange,
                onQuitClick = { platformExitApp() },
                onNavigateToHome = onNavigateToHome,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToLibrary = onNavigateToLibrary,
                onNavigateToDiscover = onNavigateToDiscover,
                onNavigateToCalendar = onNavigateToCalendar,
                onNavigateToLiveTv = onNavigateToLiveTv,
                onNavigateToMusic = onNavigateToMusic,
                contextPanelWidth = contextPanelWidth,
                page = page,
            )
            if (desktopColumnGuidesVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SettingsColumnGuideWidth)
                        .background(settingsColumnGuideBrush(horizontal = true)),
                )
            }
            Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(DesktopSettingsSidebarWidth)
                    .fillMaxSize(),
            ) {
                CompositionLocalProvider(LocalSettingsFocusRegion provides SettingsFocusRegion.Sidebar) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 22.dp, vertical = 24.dp),
                ) {
                    LaunchedEffect(Unit) {
                        SettingsCategoryOrderRepository.ensureLoaded()
                        SettingsHiddenCategoriesRepository.ensureLoaded()
                    }
                    val categoryOrder by SettingsCategoryOrderRepository.order.collectAsStateWithLifecycle()
                    val hiddenCategories by SettingsHiddenCategoriesRepository.hiddenPages
                        .collectAsStateWithLifecycle()
                    val sidebarItems = desktopSettingsSidebarItems()
                    val orderedSidebarItems = remember(sidebarItems, categoryOrder) {
                        orderDesktopSettingsSidebarItems(sidebarItems, categoryOrder)
                    }
                    // Hidden categories drop out of the list but stay in `orderedSidebarItems`, so
                    // the visibility dialog can still offer them and a reorder can keep their slot.
                    val visibleSidebarItems = remember(orderedSidebarItems, hiddenCategories) {
                        orderedSidebarItems.filterNot { it.page.name in hiddenCategories }
                    }
                    val categoriesConfigureIconVisible by SettingsHiddenCategoriesRepository
                        .configureIconVisible
                        .collectAsStateWithLifecycle()
                    var categoryVisibilityDialogVisible by remember { mutableStateOf(false) }
                    // With every category hidden there is no row to hand the page's entry to, so the
                    // heading that opens the customization dialog becomes that entry.
                    val categoriesEmpty = visibleSidebarItems.isEmpty()
                    DisposableEffect(entryRequester, focusManager, categoriesEmpty) {
                        if (categoriesEmpty && entryRequester != null) {
                            SettingsSidebarFocus.register(entryRequester, focusManager)
                        }
                        onDispose {
                            if (categoriesEmpty && entryRequester != null) {
                                SettingsSidebarFocus.unregister(entryRequester)
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        DesktopPanelSection(
                            title = stringResource(Res.string.settings_desktop_categories),
                            // Hidden on request once the user has been in the dialog; the heading
                            // row keeps its onTitleClick either way, so the panel is still
                            // reachable with no mark on it.
                            titleAction = Icons.Rounded.Tune.takeIf { categoriesConfigureIconVisible },
                            titleActionContentDescription = stringResource(
                                Res.string.settings_desktop_categories_customize,
                            ),
                            onTitleClick = { categoryVisibilityDialogVisible = true },
                            modifier = if (categoriesEmpty && entryRequester != null) {
                                Modifier.focusRequester(entryRequester)
                            } else {
                                Modifier
                            },
                        ) {
                            if (visibleSidebarItems.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.settings_desktop_categories_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.colors.textMuted,
                                )
                            } else {
                                DesktopSettingsSidebarList(
                                    entryRequester = entryRequester,
                                    items = visibleSidebarItems,
                                    allItems = orderedSidebarItems,
                                    activeSidebarPage = activeSidebarPage,
                                    currentPage = page,
                                    subItems = desktopSettingsSidebarSubItems(
                                        sidebarPage = activeSidebarPage,
                                        homescreenItems = homescreenItems,
                                    ),
                                    onPageChange = ::openInlinePage,
                                    onSubItemClick = { subItem ->
                                        if (subItem.page != page) openInlinePage(subItem.page) else closeSettingsSearch()
                                        subItem.anchor?.let { SettingsScrollAnchor.request(it) }
                                    },
                                )
                            }
                        }
                    }
                    if (categoryVisibilityDialogVisible) {
                        SettingsCategoryVisibilityDialog(
                            items = orderedSidebarItems,
                            onDismiss = {
                                categoryVisibilityDialogVisible = false
                                // Hiding the page that is open would strand the user on a category
                                // with no sidebar row to click away from. Redirected on close
                                // rather than on the toggle: changing page swaps the saveable state
                                // key underneath this dialog, which would shut it after one switch.
                                if (activeSidebarPage.name in hiddenCategories) {
                                    openInlinePage(SettingsPage.Root)
                                }
                            },
                        )
                    }
                }
                }
                if (desktopColumnGuidesVisible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(SettingsColumnGuideWidth)
                            .fillMaxHeight()
                            .background(settingsColumnGuideBrush()),
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.width(contentWidth).fillMaxHeight()) {
                    val contextPanelWidth = if (showContextPanel) DesktopSettingsContextPanelWidth else 0.dp
                    val remainingForMain = maxWidth - contextPanelWidth
                    val mainColumnWidth = if (desktopSettingsFullWidth || remainingForMain < DesktopSettingsMainColumnWidth) {
                        remainingForMain
                    } else {
                        DesktopSettingsMainColumnWidth
                    }
                    // The page content's keyboard entry: a 1dp stop at its top edge, focused when a
                    // category opens and then stepped Down onto the first row. It is focusable only
                    // for that move (see contentEntryActive), so it adds no extra stop to traversal.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(1.dp)
                            .focusRequester(contentEntryRequester)
                            .focusProperties { canFocus = contentEntryActive }
                            .focusable(),
                    )
                    Row(modifier = Modifier.fillMaxSize()) {
                        // The list's size, for headings that scroll themselves to the top of it.
                        val listViewportSize = remember { mutableStateOf(IntSize.Zero) }
                        val anchorScope = rememberCoroutineScope()
                        val anchorViewport = remember(listState) {
                            SettingsAnchorViewport(
                                listViewportSize,
                                DesktopSettingsPageTopInset,
                                scrollBy = { delta, instant ->
                                    if (instant) listState.scrollBy(delta) else listState.animateScrollBy(delta, SettingsAnchorScrollSpec)
                                },
                                scope = anchorScope,
                                scrollPosition = { listState.scrollPositionKey() },
                            )
                        }
                        // Expose the current page to every SettingsSection heading so it can build a
                        // stable favorite/scroll-anchor id and be pinned via right-click.
                        CompositionLocalProvider(
                            LocalSettingsPage provides page,
                            LocalSettingsSectionCards provides true,
                            LocalSettingsAnchorViewport provides anchorViewport,
                            LocalSettingsFocusRegion provides SettingsFocusRegion.Content,
                        ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .width(mainColumnWidth)
                                .fillMaxHeight()
                                // Hidden for the few frames a jump walks the list; see
                                // SeekSettingsAnchorIntoView.
                                .graphicsLayer { alpha = if (SettingsScrollAnchor.seeking) 0f else 1f }
                                .onSizeChanged { listViewportSize.value = it }
                                .onPlaced { anchorViewport.coordinates = it }
                                .nestedScroll(rootSearchRevealConnection)
                                // Up out of the content belongs to the row of tab icons, not to
                                // the search field that happens to sit above the right-hand rows.
                                // With the content a focus group, leaving it upward asks the top
                                // bar first; the field is still reachable by Right from the bar.
                                .focusProperties {
                                    onExit = {
                                        if (requestedFocusDirection == FocusDirection.Up &&
                                            SettingsTopBarFocus.requestEntry(focusManager)
                                        ) {
                                            cancelFocusChange()
                                        }
                                    }
                                }
                                .focusGroup()
                                // Fallback traversal for any content control that does not use
                                // [settingsNavItem] (a text field, a heading action): an arrow it
                                // does not consume still moves focus, so no focusable control in the
                                // content is a dead stop. Rows consume their arrows in the preview
                                // phase, so this only ever sees the ones they leave alone.
                                .arrowKeyFocusNavigation(releaseWhenStuck = false),
                            contentPadding = PaddingValues(
                                start = 32.dp,
                                top = DesktopSettingsPageTopInset,
                                end = 32.dp,
                                bottom = 40.dp + bottomOverlayPadding,
                            ),
                            verticalArrangement = Arrangement.spacedBy(SettingsSectionGap),
                        ) {
                // Sub-pages keep their title but no back control: the sidebar already shows the
                // parent category unfolded with this page marked current, and is the way back.
                if (page.desktopBackPage() != null) {
                    item {
                        TabletPageHeader(
                            title = stringResource(page.titleRes),
                            showBack = false,
                            onBack = {},
                        )
                    }
                }
                if (settingsSearchQuery.isNotBlank()) {
                    // The top-bar search field is shown on every page in the wide layout, so a
                    // non-blank query must render results here regardless of which page is open —
                    // otherwise typing on a non-Root page (e.g. Addons) draws nothing.
                    settingsSearchResultsContent(
                        query = settingsSearchQuery,
                        entries = searchEntries,
                        isTablet = true,
                        onTargetClick = { openSearchTarget(it) },
                    )
                } else when (page) {
                    SettingsPage.Root -> {
                        settingsSearchRootContent(
                            query = settingsSearchQuery,
                            entries = searchEntries,
                            isTablet = true,
                            showSearchField = rootSearchVisible,
                            animateSearchField = rootSearchRevealAnimating,
                            onQueryChange = { settingsSearchQuery = it },
                            onSearchFocusChange = onSettingsSearchFocusChange,
                            onTargetClick = { openSearchTarget(it) },
                        )
                        if (settingsSearchQuery.isBlank()) {
                            settingsRootContent(
                                isTablet = true,
                                onPlaybackClick = { openInlinePage(SettingsPage.Playback) },
                                onPlayerClick = { openInlinePage(SettingsPage.Player) },
                                onRandomPlayClick = { openInlinePage(SettingsPage.RandomPlay) },
                                onDiscoverClick = { openInlinePage(SettingsPage.Discover) },
                                onStreamsClick = { openInlinePage(SettingsPage.Streams) },
                                onLocalLibraryClick = { openInlinePage(SettingsPage.LocalLibrary) },
                                onGamesClick = { openInlinePage(SettingsPage.Games) },
                                onIptvClick = { openInlinePage(SettingsPage.Iptv) },
                                onAutoDownloadsClick = { openInlinePage(SettingsPage.AutoDownloads) },
                                onAppearanceClick = { openInlinePage(SettingsPage.Appearance) },
                                onAdvancedClick = { openInlinePage(SettingsPage.Advanced) },
                                onNotificationsClick = { openInlinePage(SettingsPage.Notifications) },
                                onContinueWatchingClick = { openInlinePage(SettingsPage.ContinueWatching) },
                                onAddonsClick = { openInlinePage(SettingsPage.Addons) },
                                onPluginsClick = { openInlinePage(SettingsPage.Plugins) },
                                onHomescreenClick = { openInlinePage(SettingsPage.Homescreen) },
                                onMetaScreenClick = { openInlinePage(SettingsPage.MetaScreen) },
                                onCollectionsClick = { openInlinePage(SettingsPage.Collections) },
                                onIntegrationsClick = { openInlinePage(SettingsPage.Integrations) },
                                onTraktClick = { openInlinePage(SettingsPage.TraktAuthentication) },
                                onSimklClick = { openInlinePage(SettingsPage.SimklAuthentication) },
                                onYamtrackClick = { openInlinePage(SettingsPage.YamtrackAuthentication) },
                                onSupportersContributorsClick = { openInlinePage(SettingsPage.SupportersContributors) },
                                onLicensesAttributionsClick = { openInlinePage(SettingsPage.LicensesAttributions) },
                                onCheckForUpdatesClick = onCheckForUpdatesClick,
                                onDownloadsClick = { openInlinePage(SettingsPage.Downloads) },
                                onAccountClick = { openInlinePage(SettingsPage.Account) },
                                onSwitchProfileClick = onSwitchProfile,
                                showDownloadsEntry = AppFeaturePolicy.downloadsEnabled,
                                showNotificationsEntry = AppFeaturePolicy.notificationsEnabled,
                                showPluginsEntry = AppFeaturePolicy.pluginsEnabled,
                                showAccountSection = false,
                                showGeneralSection = true,
                                showAboutSection = false,
                                showAdvancedSection = false,
                            )
                        }
                    }
                    SettingsPage.Account -> accountSettingsContent(
                        isTablet = true,
                        rememberLastProfileEnabled = rememberLastProfileEnabled,
                        onOpenProfileSelector = onSwitchProfile,
                    )
                    SettingsPage.SupportersContributors -> supportersContributorsContent(
                        isTablet = true,
                    )
                    SettingsPage.LicensesAttributions -> licensesAttributionsContent(
                        isTablet = true,
                    )
                    SettingsPage.Playback -> playbackSettingsContent(
                        isTablet = true,
                        showLoadingOverlay = showLoadingOverlay,
                        defaultPlaybackSpeed = defaultPlaybackSpeed,
                        preferredAudioLanguage = preferredAudioLanguage,
                        secondaryPreferredAudioLanguage = secondaryPreferredAudioLanguage,
                        preferredSubtitleLanguage = preferredSubtitleLanguage,
                        secondaryPreferredSubtitleLanguage = secondaryPreferredSubtitleLanguage,
                        streamReuseLastLinkEnabled = streamReuseLastLinkEnabled,
                        streamReuseLastLinkCacheHours = streamReuseLastLinkCacheHours,
                        decoderPriority = decoderPriority,
                        mapDV7ToHevc = mapDV7ToHevc,
                        tunnelingEnabled = tunnelingEnabled,
                        useLibass = useLibass,
                        libassRenderType = libassRenderType,
                        group = PlaybackSettingsGroup.VideoAudio,
                    )
                    SettingsPage.Player -> playbackSettingsContent(
                        isTablet = true,
                        showLoadingOverlay = showLoadingOverlay,
                        defaultPlaybackSpeed = defaultPlaybackSpeed,
                        preferredAudioLanguage = preferredAudioLanguage,
                        secondaryPreferredAudioLanguage = secondaryPreferredAudioLanguage,
                        preferredSubtitleLanguage = preferredSubtitleLanguage,
                        secondaryPreferredSubtitleLanguage = secondaryPreferredSubtitleLanguage,
                        streamReuseLastLinkEnabled = streamReuseLastLinkEnabled,
                        streamReuseLastLinkCacheHours = streamReuseLastLinkCacheHours,
                        decoderPriority = decoderPriority,
                        mapDV7ToHevc = mapDV7ToHevc,
                        tunnelingEnabled = tunnelingEnabled,
                        useLibass = useLibass,
                        libassRenderType = libassRenderType,
                        group = PlaybackSettingsGroup.Player,
                    )
                    SettingsPage.Discover -> discoverSettingsContent(
                        isTablet = true,
                        settings = randomPlaySettingsUiState,
                    )
                    SettingsPage.RandomPlay -> randomPlaySettingsContent(
                        isTablet = true,
                        settings = randomPlaySettingsUiState,
                    )
                    SettingsPage.Streams -> {
                        streamsSettingsContent(
                            isTablet = true,
                            onOpenStreamScoring = { openInlinePage(SettingsPage.StreamScoring) },
                        )
                        // Source choice moved here from the old Playback page: P2P, stream
                        // selection and auto-play decide which stream plays, not how it plays.
                        playbackSettingsContent(
                            isTablet = true,
                            showLoadingOverlay = showLoadingOverlay,
                            defaultPlaybackSpeed = defaultPlaybackSpeed,
                            preferredAudioLanguage = preferredAudioLanguage,
                            secondaryPreferredAudioLanguage = secondaryPreferredAudioLanguage,
                            preferredSubtitleLanguage = preferredSubtitleLanguage,
                            secondaryPreferredSubtitleLanguage = secondaryPreferredSubtitleLanguage,
                            streamReuseLastLinkEnabled = streamReuseLastLinkEnabled,
                            streamReuseLastLinkCacheHours = streamReuseLastLinkCacheHours,
                            decoderPriority = decoderPriority,
                            mapDV7ToHevc = mapDV7ToHevc,
                            tunnelingEnabled = tunnelingEnabled,
                            useLibass = useLibass,
                            libassRenderType = libassRenderType,
                            group = PlaybackSettingsGroup.Streams,
                        )
                    }
                    SettingsPage.LocalLibrary -> localLibraryContent(
                        isTablet = true,
                        state = localLibraryUiState,
                        titlesState = localLibraryTitlesState,
                    )
                    SettingsPage.AutoDownloads -> {
                        withSettingsPage(SettingsPage.Downloads) {
                            downloadsSummaryContent(
                                isTablet = true,
                                uiState = downloadsUiState,
                                selectedShowId = selectedDownloadsShowId,
                                onSelectedShowChange = onSelectedDownloadsShowChange,
                                onOpenDownload = onOpenDownload,
                                onDeleteTarget = onDownloadsDeleteTarget,
                            )
                        }
                        libraryDownloadsSection(isTablet = true, onDownloadsClick = null)
                    }
                    SettingsPage.Downloads -> downloadsSettingsContent(
                        isTablet = true,
                        uiState = downloadsUiState,
                        selectedShowId = selectedDownloadsShowId,
                        onSelectedShowChange = onSelectedDownloadsShowChange,
                        onOpenDownload = onOpenDownload,
                        onDeleteTarget = onDownloadsDeleteTarget,
                    )
                SettingsPage.WatchParty -> watchPartySettingsContent(onOpenPartyMedia)
                SettingsPage.StreamScoring -> streamScoringSection(isTablet = true)
                    SettingsPage.KeyboardShortcuts -> keyboardShortcutsContent(
                        isTablet = true,
                    )
                    SettingsPage.Appearance -> {
                            appearanceSettingsContent(
                            isTablet = true,
                            selectedTheme = selectedTheme,
                            onThemeSelected = onThemeSelected,
                            customTheme = customTheme,
                            liquidGlassNativeTabBarSupported = liquidGlassNativeTabBarSupported,
                            liquidGlassNativeTabBarEnabled = liquidGlassNativeTabBarEnabled,
                            onLiquidGlassNativeTabBarToggle = onLiquidGlassNativeTabBarToggle,
                            desktopNavigationLayout = desktopNavigationLayout,
                            onDesktopNavigationLayoutSelected = onDesktopNavigationLayoutSelected,
                            desktopAppUiScalePercent = desktopAppUiScalePercent,
                            onDesktopAppUiScalePercentChange = onDesktopAppUiScalePercentChange,
                            desktopAppUiScaleAppliesToDetails = desktopAppUiScaleAppliesToDetails,
                            onDesktopAppUiScaleAppliesToDetailsChange = onDesktopAppUiScaleAppliesToDetailsChange,
                            selectedAppLanguage = selectedAppLanguage,
                            onAppLanguageSelected = onAppLanguageSelected,
                            posterCardStyleUiState = posterCardStyleUiState,
                        )
                        withSettingsPage(SettingsPage.MetaScreen) {
                            metaScreenSettingsContent(
                                isTablet = true,
                                uiState = metaScreenSettingsUiState,
                                sectionTitle = Res.string.compose_settings_page_meta_screen,
                            )
                        }
                    }
                    SettingsPage.Advanced -> advancedSettingsContent(
                        isTablet = true,
                        rememberLastProfileEnabled = rememberLastProfileEnabled,
                        )
                    SettingsPage.Notifications -> if (AppFeaturePolicy.notificationsEnabled) {
                        notificationsSettingsContent(
                            isTablet = true,
                            uiState = episodeReleaseNotificationsUiState,
                        )
                    }
                    SettingsPage.ContinueWatching -> continueWatchingSettingsContent(
                        isTablet = true,
                        isVisible = continueWatchingPreferencesUiState.isVisible,
                        style = continueWatchingPreferencesUiState.style,
                        clickAction = continueWatchingPreferencesUiState.clickAction,
                        upNextFromFurthestEpisode = continueWatchingPreferencesUiState.upNextFromFurthestEpisode,
                        useEpisodeThumbnails = continueWatchingPreferencesUiState.useEpisodeThumbnails,
                        showUnairedNextUp = continueWatchingPreferencesUiState.showUnairedNextUp,
                        separateNextUpRow = continueWatchingPreferencesUiState.separateNextUpRow,
                        separateUpcomingRow = continueWatchingPreferencesUiState.separateUpcomingRow,
                        blurNextUp = continueWatchingPreferencesUiState.blurNextUp,
                        showResumePromptOnLaunch = continueWatchingPreferencesUiState.showResumePromptOnLaunch,
                        sortMode = continueWatchingPreferencesUiState.sortMode,
                        relativeAirCountdown = continueWatchingPreferencesUiState.relativeAirCountdown,
                    )
                    SettingsPage.ContentDiscovery -> contentDiscoveryContent(
                        isTablet = true,
                        showPluginsEntry = AppFeaturePolicy.pluginsEnabled,
                        showDownloadsEntry = AppFeaturePolicy.downloadsEnabled,
                        onAddonsClick = { openInlinePage(SettingsPage.Addons) },
                        onPluginsClick = { openInlinePage(SettingsPage.Plugins) },
                        onHomescreenClick = { openInlinePage(SettingsPage.Homescreen) },
                        onMetaScreenClick = { openInlinePage(SettingsPage.MetaScreen) },
                        onCollectionsClick = { openInlinePage(SettingsPage.Collections) },
                        onDownloadsClick = { openInlinePage(SettingsPage.Downloads) },
                    )
                    SettingsPage.Collections -> collectionsSettingsContent(
                        isTablet = true,
                        onNavigateToEditor = onOpenCollectionEditor,
                    )
                    SettingsPage.Addons -> {
                        addonsSettingsContent()
                        if (AppFeaturePolicy.pluginsEnabled) {
                            // Where search results and the sidebar's "Plugins" entry land.
                            item(key = "plugins-anchor") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .settingsScrollAnchor(SettingsScrollAnchor.searchKey("plugins")),
                                )
                            }
                            withSettingsPage(SettingsPage.Plugins) { pluginsSettingsContent() }
                        }
                    }
                    SettingsPage.Plugins -> if (AppFeaturePolicy.pluginsEnabled) pluginsSettingsContent() else addonsSettingsContent()
                    SettingsPage.Homescreen -> homescreenSettingsContent(
                        isTablet = true,
                        heroEnabled = homescreenHeroEnabled,
                        heroInfoLines = homescreenHeroInfoLines,
                        heroInfoPriority = homescreenHeroInfoPriority,
                        heroBadgePlacement = homescreenHeroBadgePlacement,
                    heroBadgeScale = homescreenHeroBadgeScale,
                        heroReleaseStatusUnavailableOnly = homescreenHeroReleaseStatusUnavailableOnly,
                        hideUnreleasedContent = homescreenHideUnreleasedContent,
                        hideWatchedContent = homescreenHideWatchedContent,
                            hideCatalogUnderline = homescreenHideCatalogUnderline,
                        catalogRowShuffleEnabled = homescreenCatalogRowShuffleEnabled,
                        adaptiveHeroEnabled = homescreenAdaptiveHeroEnabled,
                        adaptiveHeroVerticalBias = homescreenAdaptiveHeroVerticalBias,
                        heroAmbientBackgroundEnabled = homescreenHeroAmbientBackgroundEnabled,
                        tvModeEnabled = homescreenTvModeEnabled,
                        items = homescreenItems,
                        beforeCatalogs = {
                            withSettingsPage(SettingsPage.ContinueWatching) {
                                continueWatchingSettingsContent(
                                    isTablet = true,
                                    isVisible = continueWatchingPreferencesUiState.isVisible,
                                    style = continueWatchingPreferencesUiState.style,
                                    clickAction = continueWatchingPreferencesUiState.clickAction,
                                    upNextFromFurthestEpisode = continueWatchingPreferencesUiState.upNextFromFurthestEpisode,
                                    useEpisodeThumbnails = continueWatchingPreferencesUiState.useEpisodeThumbnails,
                                    showUnairedNextUp = continueWatchingPreferencesUiState.showUnairedNextUp,
                                    separateNextUpRow = continueWatchingPreferencesUiState.separateNextUpRow,
                                    separateUpcomingRow = continueWatchingPreferencesUiState.separateUpcomingRow,
                                    blurNextUp = continueWatchingPreferencesUiState.blurNextUp,
                                    showResumePromptOnLaunch = continueWatchingPreferencesUiState.showResumePromptOnLaunch,
                                    sortMode = continueWatchingPreferencesUiState.sortMode,
                                    relativeAirCountdown = continueWatchingPreferencesUiState.relativeAirCountdown,
                                )
                            }
                        },
                    )
                    SettingsPage.MetaScreen -> metaScreenSettingsContent(
                        isTablet = true,
                        uiState = metaScreenSettingsUiState,
                    )
                    SettingsPage.Integrations -> integrationsContent(
                        isTablet = true,
                        onTmdbClick = { onPageChange(SettingsPage.TmdbEnrichment) },
                        onMdbListClick = { onPageChange(SettingsPage.MdbListRatings) },
                    onQualiCacheClick = { onPageChange(SettingsPage.QualiCache) },
                    onPosterServiceClick = { onPageChange(SettingsPage.PosterService) },
                        onDebridClick = { onPageChange(SettingsPage.Debrid) },
                        onTraktClick = { onPageChange(SettingsPage.TraktAuthentication) },
                        onSimklClick = { onPageChange(SettingsPage.SimklAuthentication) },
                        onYamtrackClick = { onPageChange(SettingsPage.YamtrackAuthentication) },
                        onYouTubeAccountClick = { onPageChange(SettingsPage.YouTubeAccount) },
                        onSeekrClick = { onPageChange(SettingsPage.Seekr) },
                        onLightsClick = { onPageChange(SettingsPage.Lights) },
                        onDiscordClick = { onPageChange(SettingsPage.DiscordPresence) },
                    )
                    SettingsPage.TmdbEnrichment -> tmdbSettingsContent(
                        isTablet = true,
                        settings = tmdbSettings,
                    )
                    SettingsPage.MdbListRatings -> mdbListSettingsContent(
                        isTablet = true,
                        settings = mdbListSettings,
                    )
                    SettingsPage.QualiCache -> qualiCacheSettingsContent(
                        isTablet = true,
                        settings = qualiCacheSettings,
                    )
                    SettingsPage.PosterService -> customPosterSettingsContent(
                        isTablet = true,
                        settings = customPosterSettings,
                    )
                    SettingsPage.Games -> gamesSettingsContent(
                        isTablet = true,
                        settings = gameLibrarySettings,
                    )
                    SettingsPage.Iptv -> iptvSettingsContent(isTablet = true)
                    SettingsPage.Screensaver -> screensaverSettingsContent(
                        isTablet = true,
                        settings = screensaverSettings,
                    )
                    SettingsPage.VoiceControl -> voiceControlSettingsContent(isTablet = true) // fork:voice-control
                    SettingsPage.Preshow -> preshowSettingsContent(isTablet = true)
                    SettingsPage.Music -> musicSettingsContent(isTablet = true) // fork:music
                    SettingsPage.Debrid -> debridSettingsContent(
                        isTablet = true,
                        settings = debridSettings,
                    )
                    SettingsPage.TraktAuthentication -> traktSettingsContent(
                        isTablet = true,
                        uiState = traktAuthUiState,
                        settingsUiState = traktSettingsUiState,
                        commentsEnabled = traktCommentsEnabled,
                        onCommentsEnabledChange = TraktCommentsSettings::setEnabled,
                    )
                    SettingsPage.SimklAuthentication -> simklSettingsContent(isTablet = true, uiState = simklAuthUiState, settingsUiState = simklSettingsUiState)
                    SettingsPage.YamtrackAuthentication -> yamtrackSettingsContent(isTablet = true, settings = yamtrackSettingsUiState)
                    SettingsPage.YouTubeAccount -> youTubeAccountSettingsContent(isTablet = true)
                    SettingsPage.Seekr -> seekrSettingsContent(isTablet = true)
                    SettingsPage.Lights -> lightsSettingsContent(isTablet = true, settings = lightsSettingsUiState)
                    SettingsPage.DiscordPresence -> discordPresenceSettingsContent(
                        isTablet = true,
                        settings = discordPresenceSettings,
                        onModeChange = DiscordPresenceSettingsRepository::setMode,
                        onEpisodeArtworkChange = DiscordPresenceSettingsRepository::setEpisodeArtwork,
                        onActivityStyleChange = DiscordPresenceSettingsRepository::setActivityStyle,
                        onActivityNameChange = DiscordPresenceSettingsRepository::setActivityName,
                    )
                }
                    }
                    }
                    if (showContextPanel) {
                        DesktopSettingsContextPanel(
                            page = page,
                            selectedTheme = selectedTheme,
                            amoledEnabled = amoledEnabled,
                            liquidGlassNativeTabBarEnabled = liquidGlassNativeTabBarEnabled,
                            discordPresenceSettings = discordPresenceSettings,
                            tmdbSettings = tmdbSettings,
                            mdbListSettings = mdbListSettings,
                            debridSettings = debridSettings,
                            traktAuthUiState = traktAuthUiState,
                            simklAuthUiState = simklAuthUiState,
                            showLoadingOverlay = showLoadingOverlay,
                            defaultPlaybackSpeed = defaultPlaybackSpeed,
                            onPageChange = ::openInlinePage,
                            onCheckForUpdatesClick = onCheckForUpdatesClick,
                            columnGuidesVisible = desktopColumnGuidesVisible,
                            onRecentSearchClick = { settingsSearchQuery = it },
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
    }
}
}

internal data class DesktopSettingsSidebarItem(
    val label: String,
    val icon: ImageVector,
    val page: SettingsPage,
    /** The shipped label, kept so a rename can be compared against it and undone. */
    val defaultLabel: String = label,
)

/**
 * The sidebar list with the user's own category names applied. Every consumer goes through here,
 * so a renamed category reads the same in the sidebar, the reorder drag and the categories dialog.
 */
@Composable
private fun desktopSettingsSidebarItems(): List<DesktopSettingsSidebarItem> {
    val customNames by remember {
        SettingsCategoryNamesRepository.ensureLoaded()
        SettingsCategoryNamesRepository.names
    }.collectAsStateWithLifecycle()
    return desktopSettingsSidebarDefaultItems().map { item ->
        val custom = customNames[item.page.name]
        if (custom.isNullOrBlank()) item else item.copy(label = custom)
    }
}

@Composable
private fun desktopSettingsSidebarDefaultItems(): List<DesktopSettingsSidebarItem> = listOf(
    // Grouped by how a setup goes: sources, then look, then playback, then the rest by how often
    // they are visited. Plugins, Continue Watching, Detail Page and the downloads list have no
    // entry of their own; they are sections of Addons, Home Layout, Layout and Downloads (see
    // desktopMergedInto).
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.compose_settings_page_addons),
        icon = Icons.Rounded.AutoAwesome,
        page = SettingsPage.Addons,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.compose_settings_page_integrations),
        icon = Icons.Rounded.Link,
        page = SettingsPage.Integrations,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.compose_settings_page_homescreen),
        icon = SettingsCategoryIcons.Homescreen,
        page = SettingsPage.Homescreen,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.compose_settings_page_appearance),
        icon = Icons.Rounded.Palette,
        page = SettingsPage.Appearance,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.compose_settings_page_streams),
        icon = SettingsCategoryIcons.Streams,
        page = SettingsPage.Streams,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.compose_settings_page_video_audio),
        icon = SettingsCategoryIcons.VideoAudio,
        page = SettingsPage.Playback,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.compose_settings_page_player),
        icon = Icons.Rounded.PlayArrow,
        page = SettingsPage.Player,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.compose_settings_page_keyboard_shortcuts),
        icon = Icons.Rounded.Keyboard,
        page = SettingsPage.KeyboardShortcuts,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.compose_settings_page_discover),
        icon = Icons.Rounded.Explore,
        page = SettingsPage.Discover,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.iptv_settings_page),
        icon = Icons.Rounded.LiveTv,
        page = SettingsPage.Iptv,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.compose_settings_page_account),
        icon = Icons.Rounded.AccountCircle,
        page = SettingsPage.Account,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.party_title),
        icon = Icons.Rounded.Groups,
        page = SettingsPage.WatchParty,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.collections_header),
        icon = Icons.Rounded.CollectionsBookmark,
        page = SettingsPage.Collections,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.compose_settings_page_screensaver),
        icon = Icons.Rounded.Bedtime,
        page = SettingsPage.Screensaver,
    ),
    DesktopSettingsSidebarItem( // fork:voice-control
        label = stringResource(Res.string.compose_settings_page_voice_control),
        icon = Icons.Rounded.Mic,
        page = SettingsPage.VoiceControl,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.compose_settings_page_preshow),
        icon = Icons.Rounded.Theaters,
        page = SettingsPage.Preshow,
    ),
    DesktopSettingsSidebarItem( // fork:music
        label = stringResource(Res.string.compose_settings_page_music),
        icon = Icons.Rounded.MusicNote,
        page = SettingsPage.Music,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.random_play_title),
        icon = Icons.Rounded.Casino,
        page = SettingsPage.RandomPlay,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.compose_settings_page_local_library),
        icon = Icons.Rounded.VideoLibrary,
        page = SettingsPage.LocalLibrary,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.compose_settings_root_downloads_title),
        icon = SettingsCategoryIcons.Downloads,
        page = SettingsPage.AutoDownloads,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.compose_settings_page_games),
        icon = Icons.Rounded.SportsEsports,
        page = SettingsPage.Games,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.compose_settings_page_advanced),
        icon = SettingsCategoryIcons.Advanced,
        page = SettingsPage.Advanced,
    ),
    DesktopSettingsSidebarItem(
        label = stringResource(Res.string.compose_settings_page_licenses_attributions),
        icon = Icons.Rounded.Info,
        page = SettingsPage.LicensesAttributions,
    ),
).filter { it.page.isEnabledByFeaturePolicy() }

/** Gap between the top of the page list and its first item; also where a heading that scrolls
 * itself to the top of the list lands, so it keeps the same breathing room it has at rest. */
private val DesktopSettingsPageTopInset = 22.dp

private fun SettingsPage.desktopSidebarPage(): SettingsPage = when (this) {
    SettingsPage.Addons -> SettingsPage.Addons
    SettingsPage.Collections -> SettingsPage.Collections
    SettingsPage.ContinueWatching -> SettingsPage.Homescreen
    SettingsPage.MetaScreen -> SettingsPage.Appearance
    SettingsPage.Playback -> SettingsPage.Playback
    SettingsPage.Player -> SettingsPage.Player
    SettingsPage.RandomPlay -> SettingsPage.RandomPlay
    SettingsPage.Discover -> SettingsPage.Discover
    SettingsPage.Appearance -> SettingsPage.Appearance
    SettingsPage.Screensaver -> SettingsPage.Screensaver
    SettingsPage.VoiceControl -> SettingsPage.VoiceControl
    SettingsPage.Preshow -> SettingsPage.Preshow
    SettingsPage.Music -> SettingsPage.Music // fork:music
    SettingsPage.Homescreen -> SettingsPage.Homescreen
    SettingsPage.Plugins -> SettingsPage.Addons
    SettingsPage.Streams -> SettingsPage.Streams
    SettingsPage.LocalLibrary -> SettingsPage.LocalLibrary
    SettingsPage.Games -> SettingsPage.Games
    SettingsPage.Iptv -> SettingsPage.Iptv
    SettingsPage.AutoDownloads -> SettingsPage.AutoDownloads
    SettingsPage.Downloads -> SettingsPage.AutoDownloads
    SettingsPage.StreamScoring -> SettingsPage.Streams
    SettingsPage.KeyboardShortcuts -> SettingsPage.KeyboardShortcuts
    SettingsPage.LicensesAttributions -> SettingsPage.LicensesAttributions
    SettingsPage.Account -> SettingsPage.Account
    SettingsPage.WatchParty -> SettingsPage.WatchParty
    SettingsPage.TraktAuthentication,
    SettingsPage.SimklAuthentication -> SettingsPage.Integrations
    SettingsPage.YamtrackAuthentication,
    SettingsPage.YouTubeAccount,
    SettingsPage.Seekr,
    SettingsPage.Lights,
    SettingsPage.DiscordPresence -> SettingsPage.Integrations
    SettingsPage.Integrations,
    SettingsPage.TmdbEnrichment,
    SettingsPage.MdbListRatings,
    SettingsPage.QualiCache,
    SettingsPage.PosterService,
    SettingsPage.Debrid -> SettingsPage.Integrations
    SettingsPage.Notifications -> SettingsPage.Notifications
    SettingsPage.Advanced -> SettingsPage.Advanced
    else -> SettingsPage.Root
}

internal fun SettingsPage.desktopBackPage(): SettingsPage? = when (this) {
    SettingsPage.StreamScoring -> SettingsPage.Streams
    SettingsPage.TmdbEnrichment,
    SettingsPage.MdbListRatings,
    SettingsPage.QualiCache,
    SettingsPage.PosterService,
    SettingsPage.Debrid,
    SettingsPage.TraktAuthentication,
    SettingsPage.SimklAuthentication,
    SettingsPage.YamtrackAuthentication,
    SettingsPage.YouTubeAccount,
    SettingsPage.Seekr,
    SettingsPage.Lights,
    SettingsPage.DiscordPresence -> SettingsPage.Integrations
    else -> null
}

private fun orderDesktopSettingsSidebarItems(
    items: List<DesktopSettingsSidebarItem>,
    categoryOrder: List<String>,
): List<DesktopSettingsSidebarItem> {
    if (categoryOrder.isEmpty()) return items
    val byPage = items.associateBy { it.page.name }
    val ordered = categoryOrder.mapNotNull { byPage[it] }.toMutableList()
    // A category the saved order has never seen (one added since) goes in after the category it
    // follows by default, so a new page lands next to its siblings instead of at the bottom.
    items.forEachIndexed { index, item ->
        if (item.page.name in categoryOrder) return@forEachIndexed
        val predecessor = items.getOrNull(index - 1)
        val at = predecessor?.let { ordered.indexOf(it) } ?: -1
        if (at >= 0) ordered.add(at + 1, item) else ordered.add(item)
    }
    return ordered
}

@Composable
private fun DesktopSettingsSidebarList(
    entryRequester: FocusRequester?,
    items: List<DesktopSettingsSidebarItem>,
    allItems: List<DesktopSettingsSidebarItem>,
    activeSidebarPage: SettingsPage,
    currentPage: SettingsPage,
    subItems: List<DesktopSettingsSidebarSubItem>,
    onPageChange: (SettingsPage) -> Unit,
    onSubItemClick: (DesktopSettingsSidebarSubItem) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    // The category whose sections are unfolded. Select sets it; moving the arrow keys never does, so
    // walking down the list does not open every category it passes. A page change from elsewhere
    // (a sub-page's parent, a favourite, a search result) opens that page's category.
    var expandedPage by remember { mutableStateOf<SettingsPage?>(activeSidebarPage) }
    LaunchedEffect(activeSidebarPage) { expandedPage = activeSidebarPage }
    // One requester per category, so an arrow can move the focus to a row without changing which
    // category is open. The selected row still has [entryRequester] for the page's keyboard entry.
    val rowRequesters = remember(items.size) { List(items.size) { FocusRequester() } }
    // The row the arrow keys moved to, waiting to be scrolled to and focused. A row below the fold
    // is not composed, so its focus requester does not exist yet: the list has to bring it into
    // view before it can take the focus.
    var moveToIndex by remember { mutableIntStateOf(-1) }
    // Which category rows currently have a focused stop inside them (their own row or a control
    // within). Empty while the keys sit in the page content, which is when the selected category
    // keeps its resting zoom.
    var focusedCategories by remember { mutableStateOf(setOf<Int>()) }
    LaunchedEffect(moveToIndex) {
        if (moveToIndex < 0) return@LaunchedEffect
        val index = moveToIndex
        // Instant rather than animated: holding Up/Down re-keys this effect every repeat, and an
        // animation restarted mid-flight reads as stutter. The focused row still brings itself into
        // view, so a single press lands without the jitter.
        listState.scrollToItem(index)
        // A frame for the row that just scrolled in to attach the requester the focus goes to.
        withFrameNanos { }
        runCatching { rowRequesters.getOrNull(index)?.requestFocus() }
        moveToIndex = -1
    }
    // The selected row is where the page's keyboard focus enters, so a Left at the page's left edge
    // comes back to the categories. Registered here rather than in the row because the selected row
    // can be scrolled out of the viewport, where it has no node to focus: the fallback scrolls to
    // it and focuses it once it is composed.
    val selectedIndex by rememberUpdatedState(items.indexOfFirst { it.page == activeSidebarPage })
    DisposableEffect(entryRequester, focusManager) {
        if (entryRequester != null) {
            SettingsSidebarFocus.register(entryRequester, focusManager) {
                val index = selectedIndex
                if (index < 0) {
                    false
                } else {
                    moveToIndex = index
                    true
                }
            }
        }
        onDispose { if (entryRequester != null) SettingsSidebarFocus.unregister(entryRequester) }
    }

    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        SettingsCategoryOrderRepository.moveByIndex(
            fromIndex = from.index,
            toIndex = to.index,
            visiblePages = items.map { it.page.name },
            allPages = allItems.map { it.page.name },
        )
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.page.name }) { index, item ->
            ReorderableItem(reorderableLazyListState, key = item.page.name) {
                val selected = item.page == activeSidebarPage
                Column {
                    DesktopSettingsSidebarRow(
                        label = item.label,
                        icon = item.icon,
                        selected = selected,
                        // The selected row is where the page's keyboard focus enters. Up on the
                        // first row reaches the categories heading above it; there is no longer a
                        // dead end at the top of the list.
                        entryRequester = entryRequester.takeIf { selected },
                        moveRequester = rowRequesters.getOrNull(index),
                        // A category with sub-items shows the expand/collapse triangle; only Select
                        // changes it, so navigating over one never opens or closes it.
                        expandable = subItems.size > 1,
                        expanded = expandedPage == item.page && subItems.size > 1,
                        modifier = with(this@ReorderableItem) {
                            // The whole row is both clickable (select category) and the drag handle, so
                            // draggableHandle's immediate slop-based drag turned a click with the
                            // slightest movement into a reorder. Require a deliberate long-press before a
                            // drag begins so a normal click just selects the category.
                            Modifier.longPressDraggableHandle(
                                onDragStarted = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                            )
                        },
                        // Up and Down walk the categories by index and scroll the one they land on
                        // into view. A lazy list only composes what is on screen, so focus traversal
                        // cannot reach a row below the fold. Moving the focus does not change the
                        // open category: only Select does. Down on the open category is left to
                        // traversal, so it reaches the sections listed under it first.
                        onVertical = { direction ->
                            val intoSections = direction > 0 && expandedPage == item.page && subItems.size > 1
                            val next = (index + direction).coerceIn(0, items.lastIndex)
                            if (intoSections || next == index) {
                                false
                            } else {
                                moveToIndex = next
                                true
                            }
                        },
                        onClick = {
                            expandedPage = if (expandedPage == item.page) null else item.page
                            onPageChange(item.page)
                        },
                        idleZoom = selected && focusedCategories.isEmpty(),
                        onRowFocusChanged = { hasFocus ->
                            focusedCategories = if (hasFocus) {
                                focusedCategories + index
                            } else {
                                focusedCategories - index
                            }
                        },
                    )
                    // The open category unfolds its sections underneath, so a page's headings are
                    // one click away from the sidebar. Only one row is ever open; a single entry is
                    // not worth a tree.
                    AnimatedVisibility(
                        visible = expandedPage == item.page && subItems.size > 1,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        DesktopSettingsSidebarSubList(
                            items = subItems,
                            currentPage = currentPage,
                            onClick = onSubItemClick,
                            focusParent = {
                                // Back from a sub-entry returns to the category row above it rather
                                // than walking the page ladder (and leaving Settings) from the sidebar.
                                rowRequesters.getOrNull(index)?.let { requester ->
                                    runCatching { requester.requestFocus() }.getOrDefault(false)
                                } ?: false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopSettingsSidebarRow(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    entryRequester: FocusRequester? = null,
    moveRequester: FocusRequester? = null,
    /** The category has sub-items to unfold; shows a filled triangle (▶ collapsed, ▼ expanded). */
    expandable: Boolean = false,
    expanded: Boolean = false,
    modifier: Modifier = Modifier,
    onVertical: ((Int) -> Boolean)? = null,
    onClick: () -> Unit,
    /** Selected row keeps a smaller resting zoom while nothing in the sidebar holds the keys. */
    idleZoom: Boolean = false,
    onRowFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    val contentColor = if (selected) tokens.colors.accent else tokens.colors.textMuted
    // The nav bar's icon zoom, applied to the category's icon and label. The label scales from its
    // leading edge so it reads as growing out from the icon rather than widening around itself;
    // the scale is visual only, so the row keeps its layout height and its 8dp gaps.
    var rowFocused by remember { mutableStateOf(false) }
    // Hover takes the keys and therefore the same zoom, like the app's sidebar rows.
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    LaunchedEffect(hovered) {
        if (hovered) runCatching { (moveRequester ?: entryRequester)?.requestFocus() }
    }
    val iconScale by animateFloatAsState(
        targetValue = when {
            rowFocused || hovered -> 1.18f
            idleZoom -> 1.10f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "settingsCategoryIconScale",
    )
    val labelScale by animateFloatAsState(
        targetValue = when {
            rowFocused || hovered -> 1.08f
            idleZoom -> 1.04f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "settingsCategoryLabelScale",
    )
    // Masked across the whole row rather than per element, so a selected category reads as one
    // sweep running from its icon through its label instead of two small independent ramps. Only
    // while selected: an unselected row is muted grey and has no accent to gradient.
    val accentMask = if (selected) Modifier.accentGradientMask() else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Outside the row's focus target, like the focus ring: sees the keys land on the row.
            .onFocusChanged { state ->
                if (rowFocused != state.hasFocus) {
                    rowFocused = state.hasFocus
                    onRowFocusChanged?.invoke(state.hasFocus)
                }
            }
            .hoverable(hoverSource)
            .then(if (entryRequester != null) Modifier.focusRequester(entryRequester) else Modifier)
            .then(if (moveRequester != null) Modifier.focusRequester(moveRequester) else Modifier)
            .settingsNavItem(onActivate = onClick, onVertical = onVertical)
            .clickable(onClick = onClick)
            // Leave room inside the ring for the focused icon zoom; the 22dp glyph grows about 2dp
            // each way, so without this it crossed the left border.
            .padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 6.dp)
            .then(accentMask),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
        )
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .graphicsLayer {
                    scaleX = labelScale
                    scaleY = labelScale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // A filled triangle immediately right of the label, always visible, never a focus stop of
        // its own: it mirrors the row's selection, not a separate control.
        if (expandable) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * The section list under the selected sidebar category. A hairline guide runs down the left under
 * the category icon, tree-style, so the entries read as children of the row above rather than as
 * more categories.
 */
@Composable
private fun DesktopSettingsSidebarSubList(
    items: List<DesktopSettingsSidebarSubItem>,
    currentPage: SettingsPage,
    onClick: (DesktopSettingsSidebarSubItem) -> Unit,
    focusParent: () -> Boolean,
) {
    val tokens = MaterialTheme.nuvio
    val guideColor = tokens.colors.borderDefault
    // Up and Down walk the entries by index, as the category list above does: a section list can
    // sit inside an AnimatedVisibility that is still settling when the first arrow arrives, and
    // index-walking does not depend on the entries being laid out at their final size yet.
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
    var moveToIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(moveToIndex) {
        if (moveToIndex < 0) return@LaunchedEffect
        val index = moveToIndex
        withFrameNanos { }
        runCatching { requesters.getOrNull(index)?.requestFocus() }
        moveToIndex = -1
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = DesktopSidebarSubListGuideInset, top = 2.dp, bottom = 6.dp)
            .drawBehind {
                val x = tokens.borders.hairline.toPx() / 2f
                drawLine(
                    color = guideColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = tokens.borders.hairline.toPx(),
                )
            }
            .padding(start = DesktopSidebarSubListTextInset),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEachIndexed { index, item ->
            // A sub-page entry (TMDB under Integrations) is "current" while that page is open; a
            // section entry never is, since the page does not report which heading is in view.
            val current = item.anchor == null && item.page == currentPage
            val color = if (current) tokens.colors.accent else tokens.colors.textMuted
            val accentMask = if (current) Modifier.accentGradientMask() else Modifier
            // The sub-entry text zooms like the category label when it holds the keys, and on hover.
            var itemFocused by remember { mutableStateOf(false) }
            val hoverSource = remember { MutableInteractionSource() }
            val hovered by hoverSource.collectIsHoveredAsState()
            val scale by animateFloatAsState(
                targetValue = when {
                    itemFocused || hovered -> 1.08f
                    current -> 1.04f
                    else -> 1f
                },
                animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                label = "settingsSubItemScale",
            )
            LaunchedEffect(hovered) {
                if (hovered) runCatching { requesters.getOrNull(index)?.requestFocus() }
            }
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodySmall,
                color = color,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { itemFocused = it.hasFocus }
                    .hoverable(hoverSource)
                    .clip(RoundedCornerShape(NuvioTokens.Radius.sm))
                    .focusRequester(requesters[index])
                    .settingsNavItem(
                        onActivate = { onClick(item) },
                        onVertical = { direction ->
                            val next = index + direction
                            if (next in items.indices) {
                                moveToIndex = next
                                true
                            } else {
                                // Up from the first entry returns to the category row above, Down
                                // from the last falls to the next category below.
                                false
                            }
                        },
                        onBack = focusParent,
                    )
                    .clickable { onClick(item) }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .then(accentMask),
            )
        }
    }
}

/** Centre of the 22.dp category icon, where the sub-list's guide line hangs from. */
private val DesktopSidebarSubListGuideInset = 17.dp

/** From the guide line to the sub-list text, landing the text on the category label's column
 * (icon 22.dp + 12.dp gap) minus the 8.dp of click padding the entries carry. */
private val DesktopSidebarSubListTextInset = 15.dp

/**
 * How the settings list lands on a search, favourite or sidebar target: a quick ease, in step with
 * the rest of the app's motion. It replaces bring-into-view's own animation, a soft spring that
 * crawled the last stretch and that Compose no longer lets a caller change.
 */
private val SettingsAnchorScrollSpec = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)

/** Most frames a seek stays hidden waiting for its target to claim the jump; see below. */
private const val SettingsAnchorLandingMaxFrames = 8

/** Frames a fallback heading gets to claim a request once it is on the page; see below. */
private const val SettingsAnchorFallbackClaimFrames = 3

/**
 * Carries a pending [SettingsScrollAnchor] request to a heading that lives in a lazy item which is
 * not composed yet. The anchor mechanism is opt-in per element: the target brings itself into view
 * when it sees the request, which only works while it is composed, and most pages put one section
 * per lazy item. A sidebar section, favourite or search result below the fold was silently ignored.
 *
 * A target already on the page handles itself. Otherwise the list walks from the top a viewport at
 * a time until the target mounts, hidden while it does ([SettingsScrollAnchor.seeking]), so the user
 * sees one quick scroll onto the target rather than the crawl. The walk used to wait 200ms before
 * starting and 160ms per viewport for search results (the fallback heading's old 120ms grace):
 * well over a second, visibly stepping, for a row a few screens down. It now moves every frame and
 * only pauses where a fallback heading is on screen and needs a frame or two to claim the request.
 * A request that reaches the end unconsumed (the section is hidden by a condition the sidebar table
 * could not predict) is expired and the list put back.
 */
@Composable
internal fun SeekSettingsAnchorIntoView(listState: LazyListState) {
    val requested by SettingsScrollAnchor.requested.collectAsStateWithLifecycle()
    LaunchedEffect(requested) {
        val request = requested ?: return@LaunchedEffect
        fun pending() = SettingsScrollAnchor.requested.value?.sequence == request.sequence
        fun targetMounted() = SettingsScrollAnchor.isMounted(request.anchor) ||
            SettingsScrollAnchor.isMounted(request.fallbackAnchor)
        // A page that has just opened composes its first items on the next frame.
        withFrameNanos {}
        if (!pending() || targetMounted()) return@LaunchedEffect
        val startIndex = listState.firstVisibleItemIndex
        val startOffset = listState.firstVisibleItemScrollOffset
        SettingsScrollAnchor.seeking = true
        try {
            listState.scrollToItem(0)
            while (pending()) {
                withFrameNanos {}
                if (!pending() || SettingsScrollAnchor.isMounted(request.anchor)) break
                if (SettingsScrollAnchor.isMounted(request.fallbackAnchor)) {
                    // Its exact row isn't here, so the heading claims the jump after a frame; don't
                    // scroll it away before it does.
                    repeat(SettingsAnchorFallbackClaimFrames) { if (pending()) withFrameNanos {} }
                    if (!pending()) break
                }
                if (!listState.canScrollForward) {
                            SettingsScrollAnchor.expire(request.sequence)
                    listState.scrollToItem(startIndex, startOffset)
                    break
                }
                listState.scrollBy(listState.layoutInfo.viewportSize.height * 0.9f)
            }
            // The target consumes the request and lands itself, in one step since the list is still
            // hidden (see SettingsAnchorViewport.scrollIntoView). Stay hidden until it has: wait out
            // the claim (a few frames at most), then a couple more for the landing scroll and the
            // layout after it, so the list reappears already on the setting.
            var claimFrames = 0
            while (pending() && claimFrames < SettingsAnchorLandingMaxFrames) {
                withFrameNanos {}
                claimFrames++
            }
            repeat(3) { withFrameNanos {} }
        } finally {
            SettingsScrollAnchor.seeking = false
        }
    }
}

@Composable
private fun DesktopSettingsProfileRow(
    activeProfile: NuvioProfile?,
    profileAvatars: List<AvatarCatalogItem>,
    onClick: (() -> Unit)?,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ActiveProfileMiniAvatar(
            profile = activeProfile,
            avatars = profileAvatars,
            selected = false,
            size = 42,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activeProfile?.name?.takeIf { it.isNotBlank() } ?: "Profile",
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "HTPC",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                maxLines = 1,
            )
        }
    }
}

/**
 * The focus ring the rest of the top bar draws, for the utility icons on its left. They are
 * ordinary focus stops — [arrowKeyFocusNavigation] walks them — but an icon button draws no focus
 * mark of its own. Apply it before the focus target ([androidx.compose.foundation.clickable], or
 * the icon button's own clickable) so the observer sits outside it and sees the focus.
 */
@Composable
private fun Modifier.settingsTopBarFocusRing(onFocus: (Boolean) -> Unit = {}): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged {
            focused = it.isFocused
            onFocus(it.isFocused)
        }
        .then(if (focused) Modifier.settingsFocusRing() else Modifier)
}

/**
 * A utility icon in the Settings top bar: the shared focus ring plus the same zoom the tabs and the
 * category icons use. The button box stays the focus target and ring bounds, so the larger glyph
 * stays inside the ring.
 */
@Composable
private fun SettingsTopBarIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable (Float) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) runCatching { requester.requestFocus() } }
    val scale by animateFloatAsState(
        targetValue = if (focused || hovered) 1.18f else 1f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "settingsTopBarIconScale",
    )
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .hoverable(hoverSource)
            .focusRequester(requester)
            .settingsTopBarFocusRing { focused = it },
    ) {
        content(scale)
    }
}

/**
 * The Settings top bar's search box, asked for by the remote's Search key while Settings is the
 * active screen. The box always exists; the counter lets a request reach it without a click.
 */
internal object SettingsTopBarSearch {
    internal var focusRequests by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    /** A spoken search: the words go into the Settings box, not the video search. */
    internal var pendingText by androidx.compose.runtime.mutableStateOf<String?>(null)

    /** True only while the Settings top bar is composed, so a stale request cannot fire later. */
    internal var active by androidx.compose.runtime.mutableStateOf(false)

    fun requestFocus(): Boolean {
        if (!active) return false
        focusRequests++
        return true
    }

    /** The remote's Search key, or a voice search with no named scope: search Settings. */
    fun searchFor(query: String?) {
        pendingText = query
        requestFocus()
    }

    fun deactivate() {
        active = false
        focusRequests = 0
    }
}

@Composable
private fun DesktopSettingsTopBar(
    query: String,
    activeProfile: NuvioProfile?,
    profileAvatars: List<AvatarCatalogItem>,
    onQueryChange: (String) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    onProfileClick: (() -> Unit)?,
    onShowLatestChangelogClick: (() -> Unit)?,
    columnGuidesVisible: Boolean,
    onColumnGuidesVisibleChange: (Boolean) -> Unit,
    fullWidth: Boolean,
    onFullWidthChange: (Boolean) -> Unit,
    onQuitClick: () -> Unit,
    onNavigateToHome: (() -> Unit)?,
    onNavigateToSearch: (() -> Unit)?,
    onNavigateToLibrary: (() -> Unit)?,
    onNavigateToDiscover: (() -> Unit)?,
    onNavigateToCalendar: (() -> Unit)?,
    onNavigateToLiveTv: (() -> Unit)?,
    onNavigateToMusic: (() -> Unit)?,
    contextPanelWidth: Dp,
    page: SettingsPage,
) {
    val tokens = MaterialTheme.nuvio
    val topBarSearchRequester = remember { FocusRequester() }
    DisposableEffect(Unit) {
        SettingsTopBarSearch.active = true
        onDispose { SettingsTopBarSearch.deactivate() }
    }
    val pendingSearchText = SettingsTopBarSearch.pendingText
    LaunchedEffect(pendingSearchText) {
        pendingSearchText?.let { text ->
            onQueryChange(text)
            SettingsTopBarSearch.pendingText = null
        }
    }
    val topBarSearchRequests = SettingsTopBarSearch.focusRequests
    var handledTopBarSearchRequests by remember { mutableIntStateOf(topBarSearchRequests) }
    LaunchedEffect(topBarSearchRequests) {
        if (topBarSearchRequests == handledTopBarSearchRequests) return@LaunchedEffect
        handledTopBarSearchRequests = topBarSearchRequests
        withFrameNanos { }
        runCatching { topBarSearchRequester.requestFocus() }
    }
    // Set by a Right pressed in the utility-icon row. Spatial focus then lands on the tab row's
    // whole focus target, which would otherwise keep its last index (Calendar, the right edge) even
    // though the keys arrived from its left. The tab row reads this to enter at Home instead.
    var enterTabRowFromLeft by remember { mutableStateOf(false) }
    // The search field's clear X sits in the window's top-right corner, under the fullscreen
    // button's hover zone; while it is showing, that button stays out of the way.
    val clearButtonVisible = query.isNotEmpty()
    DisposableEffect(clearButtonVisible) {
        DesktopFullscreenHoverButtonState.suppressed = clearButtonVisible
        onDispose { DesktopFullscreenHoverButtonState.suppressed = false }
    }
    // The shell is centred in the window, so centring the nav icons on the whole bar puts them
    // under the floating nav bar's own slots (it is the same width, centred in the window).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            // The utility icons and the search field beside the tab row are ordinary focusable
            // controls: the arrows walk them, and the tab row releases a Left at its first entry so
            // this reaches the icons drawn to its left. Compose Desktop moves focus on Tab only.
            .arrowKeyFocusNavigation(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 32.dp)
                    // Remember which side a Right leaves from: entering the tab row from the left
                    // starts at Home, entering it from the search field still starts at the right
                    // edge. Any other key drops the flag so a stale one cannot move the entry.
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            enterTabRowFromLeft = event.key == Key.DirectionRight
                        }
                        false
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                var profileFocused by remember { mutableStateOf(false) }
                val profileRequester = remember { FocusRequester() }
                val profileHoverSource = remember { MutableInteractionSource() }
                val profileHovered by profileHoverSource.collectIsHoveredAsState()
                LaunchedEffect(profileHovered) {
                    if (profileHovered) runCatching { profileRequester.requestFocus() }
                }
                val profileScale by animateFloatAsState(
                    targetValue = if (profileFocused || profileHovered) 1.18f else 1f,
                    animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                    label = "settingsTopBarProfileScale",
                )
                Box(
                    modifier = Modifier
                        .hoverable(profileHoverSource)
                        .focusRequester(profileRequester)
                        .settingsTopBarFocusRing { profileFocused = it }
                        .clickable(enabled = onProfileClick != null) { onProfileClick?.invoke() }
                        // Room for the focused zoom, so the avatar stays inside its ring.
                        .padding(4.dp),
                ) {
                    Box(
                        modifier = Modifier.graphicsLayer {
                            scaleX = profileScale
                            scaleY = profileScale
                        },
                    ) {
                        ActiveProfileMiniAvatar(
                            profile = activeProfile,
                            avatars = profileAvatars,
                            selected = false,
                            size = 34,
                        )
                    }
                }
                // Each icon drawn in the settings glass (see settingsGlassGlyph) so the bar reads as part
                // of the page. The two toggles are clear glass when off and take the accent when on.
                SettingsTopBarIconButton(
                    onClick = { onShowLatestChangelogClick?.invoke() },
                    enabled = onShowLatestChangelogClick != null,
                ) { scale ->
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = null,
                        modifier = Modifier
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .settingsGlassGlyph(lit = onShowLatestChangelogClick != null),
                    )
                }
                SettingsTopBarIconButton(
                    onClick = { onColumnGuidesVisibleChange(!columnGuidesVisible) },
                ) { scale ->
                    Icon(
                        imageVector = Icons.Rounded.ViewColumn,
                        contentDescription = stringResource(Res.string.settings_desktop_toggle_column_guides),
                        modifier = Modifier
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .settingsGlassGlyph(lit = columnGuidesVisible),
                    )
                }
                // Centred column set vs. the whole window; lit like the guides toggle when it is on.
                SettingsTopBarIconButton(
                    onClick = { onFullWidthChange(!fullWidth) },
                ) { scale ->
                    Icon(
                        imageVector = Icons.Rounded.WidthFull,
                        contentDescription = stringResource(Res.string.settings_desktop_toggle_full_width),
                        modifier = Modifier
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .settingsGlassGlyph(lit = fullWidth),
                    )
                }
                SettingsTopBarIconButton(onClick = onQuitClick) { scale ->
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .settingsGlassGlyph(),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (contextPanelWidth == 0.dp) {
                    SettingsSearchField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onFocusChange = onSearchFocusChange,
                        focusRequester = topBarSearchRequester,
                        onSubmit = {
                            if (query.isNotBlank()) {
                                SettingsSearchHistoryRepository.record(query, null)
                                SettingsSearchResultFocus.request()
                            }
                        },
                        modifier = Modifier
                            .padding(end = 18.dp)
                            .height(44.dp)
                            .widthIn(min = 240.dp, max = 320.dp),
                    )
                }
            }
            if (contextPanelWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .width(contextPanelWidth)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    SettingsSearchField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onFocusChange = onSearchFocusChange,
                        focusRequester = topBarSearchRequester,
                        onSubmit = {
                            if (query.isNotBlank()) {
                                SettingsSearchHistoryRepository.record(query, null)
                                SettingsSearchResultFocus.request()
                            }
                        },
                        modifier = Modifier
                            .padding(horizontal = 18.dp)
                            .height(44.dp)
                            .widthIn(min = 240.dp, max = 268.dp),
                    )
                }
            }
        }
        DesktopSettingsRootNavigation(
            enterFromLeft = { enterTabRowFromLeft },
            consumeEnterFromLeft = { enterTabRowFromLeft = false },
            onNavigateToHome = onNavigateToHome,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToLibrary = onNavigateToLibrary,
            onNavigateToDiscover = onNavigateToDiscover,
            onNavigateToCalendar = onNavigateToCalendar,
            onNavigateToLiveTv = onNavigateToLiveTv,
            onNavigateToMusic = onNavigateToMusic,
            pageOwner = page,
        )
    }
}

@Composable
private fun DesktopSettingsRootNavigation(
    /** True when the keys reached this row by a Right pressed in the utility icons on its left. */
    enterFromLeft: () -> Boolean,
    consumeEnterFromLeft: () -> Unit,
    onNavigateToHome: (() -> Unit)?,
    onNavigateToSearch: (() -> Unit)?,
    onNavigateToLibrary: (() -> Unit)?,
    onNavigateToDiscover: (() -> Unit)?,
    onNavigateToCalendar: (() -> Unit)?,
    onNavigateToLiveTv: (() -> Unit)?,
    onNavigateToMusic: (() -> Unit)?,
    pageOwner: SettingsPage,
) {
    // Settings hides the top bar entirely, so this row is the way to the tabs from here. It holds the
    // same entries in the same order (NavBarLayout.kt), each shown only while its tab is (a button for
    // a hidden tab would do nothing, since navigation to it is refused), and is laid out like the
    // floating bar's quadrants (navBarSlotWidth a slot, 6dp inset, 2dp gaps) with Calendar in the
    // profile/Settings slot, so each icon sits where its tab does.
    val entries = com.nuvio.app.features.navbar.rememberNavBarEntries()
    // The album cover, as on the floating bar: selecting it expands the mini player and hands it
    // the keys. It sits after Calendar, next to the utility icons.
    val musicState by com.nuvio.app.features.music.MusicPlayback.player.state.collectAsStateWithLifecycle()
    val musicRowShown = musicState.isActive
    val calendarIndex = entries.size
    val musicIndex = entries.size + 1
    val slotCount = entries.size + 1 + if (musicRowShown) 1 else 0

    // Keyboard: this row is the bar across the top while Settings is open, the counterpart of the
    // floating bar the tabs show. Up at the top of the page reaches it, Left and Right walk it, Select
    // opens the icon under the cursor and Down, Back or Dismiss give the keys back to the page.
    //
    // Only where the app's desktop shortcuts live (the H key and the page root's own entry): on a
    // tablet the row is drawn but has no keyboard of this kind behind it, and claiming the keys there
    // would swallow Up without anything to give it to.
    val keyboard = isDesktop && onNavigateToHome != null
    val focusManager = LocalFocusManager.current
    val barFocusRequester = remember { FocusRequester() }
    var barHasFocus by remember { mutableStateOf(false) }
    // Calendar, the slot nearest the page's own edge, is where the keys land first, as the
    // profile/Settings slot is on the floating bar.
    var barIndex by remember { mutableIntStateOf(entries.size) }
    DisposableEffect(keyboard) {
        RootNavFocus.settingsTopBarAvailable = keyboard
        onDispose { RootNavFocus.settingsTopBarAvailable = false }
    }
    // Up released at the top of the page comes here, the way Left at its edge goes to the categories.
    DisposableEffect(barFocusRequester, focusManager, keyboard) {
        if (keyboard) {
            SettingsTopBarFocus.register(barFocusRequester, focusManager) {
                consumeEnterFromLeft()
                barIndex = entries.size
                if (!barHasFocus) RootNavFocus.rememberContentFocus(pageOwner, focusManager)
            }
        }
        onDispose { SettingsTopBarFocus.unregister(barFocusRequester) }
    }
    TopBarLeaveRestoresContent(
        topBarHasFocus = { barHasFocus },
        pageOwner = { pageOwner },
    )

    LaunchedEffect(barFocusRequester) {
        RootNavFocus.topBarEnterRequests.collect {
            consumeEnterFromLeft()
            barIndex = entries.size
            if (!barHasFocus) RootNavFocus.rememberContentFocus(pageOwner, focusManager)
            runCatching { barFocusRequester.requestFocus() }
        }
    }

    // Entries this screen has no callback for go through the app's navigation requests.
    fun request(entry: com.nuvio.app.features.navbar.NavBarEntry): () -> Unit =
        { com.nuvio.app.features.navbar.NavBarNavigation.request(entry) }
    fun onClickOf(slot: Int): (() -> Unit)? = when {
        musicRowShown && slot == musicIndex -> ({ com.nuvio.app.features.music.MusicPlayback.requestBarFocus() })
        slot == calendarIndex -> onNavigateToCalendar
        else -> when (val entry = entries.getOrNull(slot)) {
        null -> null
        com.nuvio.app.features.navbar.NavBarEntry.Home -> onNavigateToHome
        com.nuvio.app.features.navbar.NavBarEntry.Search -> onNavigateToSearch
        com.nuvio.app.features.navbar.NavBarEntry.Discover -> onNavigateToDiscover
        com.nuvio.app.features.navbar.NavBarEntry.LiveTv -> onNavigateToLiveTv
        com.nuvio.app.features.navbar.NavBarEntry.Library -> onNavigateToLibrary
        com.nuvio.app.features.navbar.NavBarEntry.YouTube -> request(entry) // fork:youtube-mode
            com.nuvio.app.features.navbar.NavBarEntry.Music -> onNavigateToMusic ?: request(entry) // fork:music
            com.nuvio.app.features.navbar.NavBarEntry.Games -> request(entry)
        }
    }

    Row(
        modifier = Modifier
            .width(com.nuvio.app.features.navbar.navBarSlotWidth(slotCount) * slotCount)
            .padding(horizontal = 6.dp)
            .then(
                if (keyboard) {
                    Modifier
                        .focusRequester(barFocusRequester)
                        // Before focusable(), as every focus observer here is: Compose only reports
                        // focus to one that sits outside the focus target.
                        .onFocusChanged { state ->
                            val gained = state.hasFocus && !barHasFocus
                            barHasFocus = state.hasFocus
                            // Arrived by Right from the utility icons: the first tab (Home) is the
                            // icon immediately to the right of the power button.
                            if (gained && enterFromLeft()) {
                                consumeEnterFromLeft()
                                barIndex = 0
                            }
                        }
                        .focusable()
                        .topBarKeyNavigation(
                            index = { barIndex },
                            count = { slotCount },
                            onMove = { barIndex = it },
                            onActivate = { onClickOf(barIndex)?.invoke() },
                            onLeave = { RootNavFocus.leaveTopBar() },
                        )
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        entries.forEachIndexed { entryIndex, entry ->
            key(entry) {
                val focused = barHasFocus && barIndex == entryIndex
                when (entry) {
                    com.nuvio.app.features.navbar.NavBarEntry.Home -> DesktopSettingsNavIcon(
                        onClick = onNavigateToHome,
                        focused = focused,
                        onHoverFocus = {
                            barIndex = entryIndex
                            runCatching { barFocusRequester.requestFocus() }
                        },
                        contentDescription = stringResource(Res.string.compose_nav_home),
                        imageVector = Icons.Filled.Home,
                    )
                    com.nuvio.app.features.navbar.NavBarEntry.Search -> DesktopSettingsNavIcon(
                        onClick = onNavigateToSearch,
                        focused = focused,
                        onHoverFocus = {
                            barIndex = entryIndex
                            runCatching { barFocusRequester.requestFocus() }
                        },
                        contentDescription = stringResource(Res.string.compose_nav_search),
                        painter = painterResource(Res.drawable.sidebar_search),
                    )
                    com.nuvio.app.features.navbar.NavBarEntry.Discover -> DesktopSettingsNavIcon(
                        onClick = onNavigateToDiscover,
                        focused = focused,
                        onHoverFocus = {
                            barIndex = entryIndex
                            runCatching { barFocusRequester.requestFocus() }
                        },
                        contentDescription = stringResource(Res.string.compose_nav_discover),
                        imageVector = Icons.Rounded.Explore,
                    )
                    com.nuvio.app.features.navbar.NavBarEntry.LiveTv -> DesktopSettingsNavIcon(
                        onClick = onNavigateToLiveTv,
                        focused = focused,
                        onHoverFocus = {
                            barIndex = entryIndex
                            runCatching { barFocusRequester.requestFocus() }
                        },
                        contentDescription = stringResource(Res.string.iptv_settings_page),
                        imageVector = Icons.Rounded.LiveTv,
                    )
                    com.nuvio.app.features.navbar.NavBarEntry.Library -> DesktopSettingsNavIcon(
                        onClick = onNavigateToLibrary,
                        focused = focused,
                        onHoverFocus = {
                            barIndex = entryIndex
                            runCatching { barFocusRequester.requestFocus() }
                        },
                        contentDescription = stringResource(Res.string.compose_nav_library),
                        painter = painterResource(Res.drawable.sidebar_library),
                    )
                    com.nuvio.app.features.navbar.NavBarEntry.YouTube -> DesktopSettingsNavIcon( // fork:youtube-mode
                        onClick = request(entry),
                        focused = focused,
                        onHoverFocus = {
                            barIndex = entryIndex
                            runCatching { barFocusRequester.requestFocus() }
                        },
                        contentDescription = "YouTube",
                        imageVector = Icons.Rounded.SmartDisplay,
                    )
                    com.nuvio.app.features.navbar.NavBarEntry.Music -> DesktopSettingsNavIcon( // fork:music
                        onClick = onNavigateToMusic ?: request(entry),
                        focused = focused,
                        onHoverFocus = {
                            barIndex = entryIndex
                            runCatching { barFocusRequester.requestFocus() }
                        },
                        contentDescription = "Music",
                        imageVector = Icons.Rounded.MusicNote,
                    )
                    com.nuvio.app.features.navbar.NavBarEntry.Games -> DesktopSettingsNavIcon(
                        onClick = request(entry),
                        focused = focused,
                        onHoverFocus = {
                            barIndex = entryIndex
                            runCatching { barFocusRequester.requestFocus() }
                        },
                        contentDescription = "Games",
                        imageVector = Icons.Rounded.SportsEsports,
                    )
                }
            }
        }
        // Calendar is a pushed route rather than a tab, so it has no sidebar entry to fall back
        // on; from Settings the only other ways in are the hotkey or the Library header icon.
        DesktopSettingsNavIcon(
            onClick = onNavigateToCalendar,
            focused = barHasFocus && barIndex == calendarIndex,
            onHoverFocus = {
                barIndex = calendarIndex
                runCatching { barFocusRequester.requestFocus() }
            },
            contentDescription = stringResource(Res.string.calendar_title),
            imageVector = Icons.Rounded.DateRange,
        )
        if (musicRowShown) {
            DesktopSettingsNavIcon(
                onClick = { com.nuvio.app.features.music.MusicPlayback.requestBarFocus() },
                focused = barHasFocus && barIndex == musicIndex,
                onHoverFocus = {
                    barIndex = musicIndex
                    runCatching { barFocusRequester.requestFocus() }
                },
                contentDescription = "Now playing",
            ) {
                val cover = musicState.coverUrl
                if (cover != null) {
                    com.nuvio.app.core.ui.NuvioAsyncImage(
                        model = cover,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/**
 * The column guides as slivers of the settings glass rather than flat accent paint: the accent seen
 * through the cards' white edge, catching the light where each line starts and thinning out along
 * it, the same falloff the glass glyphs' sheen has. The horizontal guide catches it at both ends,
 * where it meets the shell's two vertical lines.
 */
@Composable
private fun settingsColumnGuideBrush(horizontal: Boolean = false): Brush {
    val accent = MaterialTheme.nuvio.colors.accent
    val catchLight = lerp(accent, Color.White, 0.5f).copy(alpha = 0.62f)
    val sheen = lerp(accent, Color.White, 0.2f).copy(alpha = 0.40f)
    val body = lerp(accent, Color.White, 0.1f).copy(alpha = 0.22f)
    return if (horizontal) {
        Brush.horizontalGradient(
            0f to catchLight,
            0.08f to sheen,
            0.5f to body,
            0.92f to sheen,
            1f to catchLight,
        )
    } else {
        Brush.verticalGradient(
            0f to catchLight,
            0.12f to sheen,
            1f to body,
        )
    }
}

/** A full pixel, as the glass edges are: a half-pixel line goes soft and patchy at 100% scale. */
private val SettingsColumnGuideWidth = 1.dp


@Composable
private fun RowScope.DesktopSettingsNavIcon(
    onClick: (() -> Unit)?,
    contentDescription: String,
    imageVector: ImageVector? = null,
    painter: Painter? = null,
    focused: Boolean = false,
    onHoverFocus: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val enabled = onClick != null
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onHoverFocus?.invoke() }
    // Same zoom as the app's sidebar icons and the Settings categories, so the row reads as the
    // same navigation surface. The 48dp button stays the ring and focus bounds.
    val scale by animateFloatAsState(
        targetValue = if (focused || hovered) 1.18f else 1f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "settingsRootNavIconScale",
    )
    val scaleModifier = Modifier
        .size(22.dp)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    // Vector glyphs are masked through the glass tint; a cover image must keep its own colours.
    val iconModifier = scaleModifier.settingsGlassGlyph(lit = enabled)
    Box(
        modifier = Modifier.weight(1f).hoverable(hoverSource),
        contentAlignment = Alignment.Center,
    ) {
        val ringShape = RoundedCornerShape(8.dp)
        Box(
            modifier = Modifier
                // The ring marks the icon the arrow keys are on, as the floating bar's quadrants ring
                // theirs: while it is up the keys live on the bar, not on the page behind it.
                .then(
                    if (focused) {
                        Modifier.border(2.dp, Color(0xFF9ECAFF).copy(alpha = .9f), ringShape)
                    } else {
                        Modifier
                    },
                )
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = { onClick?.invoke() },
                enabled = enabled,
                // The bar itself is the keyboard stop: its arrow keys walk the slots by index. An
                // icon that took the focus on its own would sit outside that index and swallow the
                // next key.
                modifier = Modifier.focusProperties { canFocus = false },
            ) {
                when {
                    content != null -> Box(scaleModifier, contentAlignment = Alignment.Center) { content() }
                    imageVector != null -> Icon(imageVector, contentDescription, iconModifier)
                    painter != null -> Icon(painter, contentDescription, iconModifier)
                }
            }
        }
    }
}

@Composable
private fun DesktopSettingsContextPanel(
    page: SettingsPage,
    selectedTheme: AppTheme,
    amoledEnabled: Boolean,
    liquidGlassNativeTabBarEnabled: Boolean,
    discordPresenceSettings: DiscordPresenceSettings,
    tmdbSettings: TmdbSettings,
    mdbListSettings: MdbListSettings,
    debridSettings: DebridSettings,
    traktAuthUiState: TraktAuthUiState,
    simklAuthUiState: SimklAuthUiState,
    showLoadingOverlay: Boolean,
    defaultPlaybackSpeed: Float,
    onPageChange: (SettingsPage) -> Unit,
    onCheckForUpdatesClick: (() -> Unit)?,
    columnGuidesVisible: Boolean,
    onRecentSearchClick: (String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val playerSettings by PlayerSettingsRepository.uiState.collectAsStateWithLifecycle()
    Surface(
        modifier = Modifier
            .width(DesktopSettingsContextPanelWidth)
            .fillMaxHeight(),
        // Transparent so the shell's sky runs under the panel too.
        color = Color.Transparent,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (columnGuidesVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(SettingsColumnGuideWidth)
                        .fillMaxHeight()
                        .background(settingsColumnGuideBrush()),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(SettingsColumnGuideWidth)
                        .fillMaxHeight()
                        .background(settingsColumnGuideBrush()),
                )
            }
            CompositionLocalProvider(LocalSettingsFocusRegion provides SettingsFocusRegion.ContextPanel) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                LaunchedEffect(Unit) { SettingsFavoritesRepository.ensureLoaded() }
                val favorites by SettingsFavoritesRepository.favorites.collectAsStateWithLifecycle()
                DesktopPanelSection(title = stringResource(Res.string.settings_desktop_favorites)) {
                    if (favorites.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.settings_desktop_favorites_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.colors.textMuted,
                        )
                    } else {
                        DesktopFavoritesList(
                            favorites = favorites,
                            onPageChange = onPageChange,
                        )
                    }
                }
                LaunchedEffect(Unit) { SettingsSearchHistoryRepository.ensureLoaded() }
                val recentSearches by SettingsSearchHistoryRepository.recent.collectAsStateWithLifecycle()
                // Only once there is something to show: an empty "no searches yet" block under the
                // favourites would be clutter on every page.
                if (recentSearches.isNotEmpty()) {
                    DesktopPanelSection(title = stringResource(Res.string.settings_desktop_recent_searches)) {
                        Column {
                            recentSearches.forEach { recent ->
                                val target = recent.page?.let { runCatching { SettingsPage.valueOf(it) }.getOrNull() }
                                DesktopActionRow(
                                    icon = Icons.Rounded.History,
                                    title = recent.query,
                                    subtitle = target?.let { stringResource(it.titleRes) }.orEmpty(),
                                    // Right-click forgets it, as it unpins a favourite.
                                    onSecondaryClick = { SettingsSearchHistoryRepository.remove(recent.query) },
                                ) {
                                    onRecentSearchClick(recent.query)
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun DesktopFavoritesList(
    favorites: List<SettingsFavorite>,
    onPageChange: (SettingsPage) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        SettingsFavoritesRepository.moveByIndex(from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
        state = listState,
    ) {
        itemsIndexed(favorites, key = { _, favorite -> favorite.anchor }) { _, favorite ->
            ReorderableItem(reorderableLazyListState, key = favorite.anchor) {
                val target = runCatching { SettingsPage.valueOf(favorite.page) }.getOrNull()
                val subtitle = target?.let { stringResource(it.titleRes) }.orEmpty()
                DesktopActionRow(
                    icon = Icons.Rounded.Star,
                    title = favorite.title,
                    subtitle = subtitle,
                    modifier = with(this@ReorderableItem) {
                        Modifier.draggableHandle(
                            onDragStarted = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        )
                    },
                    onSecondaryClick = {
                        SettingsFavoritesRepository.remove(favorite.anchor)
                    },
                ) {
                    if (target != null) {
                        onPageChange(target)
                        SettingsScrollAnchor.request(favorite.anchor)
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopPanelSection(
    title: String,
    content: @Composable () -> Unit,
) {
    DesktopPanelSection(
        title = title,
        titleAction = null,
        titleActionContentDescription = null,
        onTitleClick = null,
        content = content,
    )
}

@Composable
private fun DesktopPanelSection(
    title: String,
    titleAction: ImageVector?,
    titleActionContentDescription: String?,
    onTitleClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                // A clickable heading is a control the arrow keys must be able to reach, and one
                // that has to show it is focused. Applying the item before clickable lets the
                // focus ring sit outside the click target.
                .then(
                    if (onTitleClick != null) {
                        Modifier.settingsNavItem(onActivate = onTitleClick)
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            if (titleAction != null) {
                // The heading is the control, so it needs a mark that says so - a bare title that
                // happens to be clickable is not discoverable. Muted rather than accent: this is an
                // affordance on a heading, not an action the sidebar should lead with.
                Icon(
                    imageVector = titleAction,
                    contentDescription = titleActionContentDescription,
                    tint = tokens.colors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
        }
    }
}

@Composable
private fun DesktopInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tokens.colors.textMuted,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DesktopActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onSecondaryClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = modifier
            .fillMaxWidth()
            .secondaryClick(onSecondaryClick)
            .settingsNavItem(onActivate = onClick)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tokens.colors.textMuted,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                modifier = settingsRowTitleAccentMask(title),
                style = MaterialTheme.typography.bodyMedium,
                color = settingsRowTitleColor(title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailingContent?.invoke()
    }
}

private fun enabledLabel(enabled: Boolean): String = if (enabled) "Enabled" else "Disabled"

private fun connectedLabel(connected: Boolean): String = if (connected) localizedString(Res.string.settings_connected) else localizedString(Res.string.settings_not_connected)

/**
 * The Watch party page: join with a code or an invite link, or manage the party you are in.
 * Upstream's PartyScreen route, shown inside Settings here (see PartyInvites for invite links).
 */
private fun androidx.compose.foundation.lazy.LazyListScope.watchPartySettingsContent(
    onOpenPartyMedia: (com.nuvio.app.features.party.PartyMedia) -> Unit,
) {
    item(key = "watch_party_page") {
        val invite by com.nuvio.app.features.party.PartyInvites.latest.collectAsStateWithLifecycle()
        androidx.compose.runtime.key(invite?.id) {
            val focusManager = LocalFocusManager.current
            com.nuvio.app.features.party.PartyPage(
                initialCode = invite?.code.orEmpty(),
                onWatch = onOpenPartyMedia,
                // Back from the Watch Party content returns to the settings sidebar rather than
                // leaving Settings; the next Back from the sidebar exits Settings as usual.
                onBack = { runCatching { SettingsSidebarFocus.requestEntry(focusManager) } },
            )
        }
    }
}

/** The list's scroll position packed into one number; see [SettingsAnchorViewport.scrollPosition]. */
internal fun LazyListState.scrollPositionKey(): Long =
    (firstVisibleItemIndex.toLong() shl 32) or (firstVisibleItemScrollOffset.toLong() and 0xffffffffL)
