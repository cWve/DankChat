package com.flxrs.dankchat.main

import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.flxrs.dankchat.DankChatViewModel
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.MainActivityBinding
import com.flxrs.dankchat.preferences.*
import com.flxrs.dankchat.preferences.screens.*
import com.flxrs.dankchat.service.DataRepository
import com.flxrs.dankchat.service.NotificationService
import com.flxrs.dankchat.utils.extensions.navigateSafe
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    @Inject
    lateinit var dankChatPreferences: DankChatPreferenceStore

    private val viewModel: DankChatViewModel by viewModels()
    private val pendingChannelsToClear = mutableListOf<String>()
    private val navController: NavController by lazy { findNavController(R.id.main_content) }
    private val windowInsetsController: WindowInsetsControllerCompat? by lazy { ViewCompat.getWindowInsetsController(binding.root) }
    private var bindingRef: MainActivityBinding? = null
    private val binding get() = bindingRef!!

    private val twitchServiceConnection = TwitchServiceConnection()
    var notificationService: NotificationService? = null
    var isBound = false
    var channelToOpen = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val isTrueDarkModeEnabled = preferences.getBoolean(getString(R.string.preference_true_dark_theme_key), false)
        val isDynamicColorAvailable = DynamicColors.isDynamicColorAvailable()
        when {
            isTrueDarkModeEnabled && isDynamicColorAvailable -> DynamicColors.applyIfAvailable(this, R.style.AppTheme_TrueDarkOverlay)
            isTrueDarkModeEnabled                            -> setTheme(R.style.AppTheme_TrueDarkTheme)
            else                                             -> DynamicColors.applyIfAvailable(this)
        }

        bindingRef = DataBindingUtil.setContentView(this, R.layout.main_activity)

        if (dankChatPreferences.isLoggedIn && dankChatPreferences.oAuthKey.isNullOrBlank()) {
            dankChatPreferences.clearLogin()
        }

        viewModel.commands
            .flowWithLifecycle(lifecycle, minActiveState = Lifecycle.State.CREATED)
            .onEach {
                when (it) {
                    DataRepository.ServiceEvent.Shutdown -> handleShutDown()
                }
            }
            .launchIn(lifecycleScope)
    }

    override fun onDestroy() {
        super.onDestroy()
        bindingRef = null
        if (!isChangingConfigurations) {
            handleShutDown()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isBound) Intent(this, NotificationService::class.java).also {
            try {
                isBound = true
                ContextCompat.startForegroundService(this, it)
                bindService(it, twitchServiceConnection, Context.BIND_AUTO_CREATE)
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            if (!isChangingConfigurations) {
                notificationService?.shouldNotifyOnMention = true
            }

            isBound = false
            try {
                unbindService(twitchServiceConnection)
            } catch (t: Throwable) {
                Log.e(TAG, Log.getStackTraceString(t))
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val channelExtra = intent?.getStringExtra(OPEN_CHANNEL_KEY) ?: ""
        channelToOpen = channelExtra
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        when (pref.fragment.substringAfterLast(".")) {
            AppearanceSettingsFragment::class.java.simpleName    -> caller.navigateSafe(R.id.action_overviewSettingsFragment_to_appearanceSettingsFragment)
            NotificationsSettingsFragment::class.java.simpleName -> caller.navigateSafe(R.id.action_overviewSettingsFragment_to_notificationsSettingsFragment)
            ChatSettingsFragment::class.java.simpleName          -> caller.navigateSafe(R.id.action_overviewSettingsFragment_to_chatSettingsFragment)
            ToolsSettingsFragment::class.java.simpleName         -> caller.navigateSafe(R.id.action_overviewSettingsFragment_to_toolsSettingsFragment)
            DeveloperSettingsFragment::class.java.simpleName     -> caller.navigateSafe(R.id.action_overviewSettingsFragment_to_developerSettingsFragment)
            else                                                 -> return false
        }
        return true
    }

    fun clearNotificationsOfChannel(channel: String) = when {
        isBound && notificationService != null -> notificationService?.setActiveChannel(channel)
        else                                   -> pendingChannelsToClear += channel
    }

    fun setTTSEnabled(enabled: Boolean) = notificationService?.setTTSEnabled(enabled)

    fun setFullScreen(enabled: Boolean, changeActionBarVisibility: Boolean = true) {
        WindowCompat.setDecorFitsSystemWindows(window, !enabled)
        when {
            enabled -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isInMultiWindowMode) {
                    windowInsetsController?.apply {
                        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
                if (changeActionBarVisibility) {
                    supportActionBar?.hide()
                }
            }
            else    -> {
                windowInsetsController?.show(WindowInsetsCompat.Type.systemBars())
                if (changeActionBarVisibility) {
                    supportActionBar?.show()
                }
            }
        }
    }

    private fun handleShutDown() {
        stopService(Intent(this, NotificationService::class.java))
        finish()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private inner class TwitchServiceConnection : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as NotificationService.LocalBinder
            notificationService = binder.service
            isBound = true

            if (pendingChannelsToClear.isNotEmpty()) {
                pendingChannelsToClear.forEach { notificationService?.setActiveChannel(it) }
                pendingChannelsToClear.clear()
            }

            if (!viewModel.started) {
                val ttsEnabledKey = getString(R.string.preference_tts_key)
                val ttsEnabled = PreferenceManager.getDefaultSharedPreferences(this@MainActivity).getBoolean(ttsEnabledKey, false)
                binder.service.setTTSEnabled(ttsEnabled)
            }

            val oauth = dankChatPreferences.oAuthKey.orEmpty()
            val name = dankChatPreferences.userName.orEmpty()
            val channels = dankChatPreferences.getChannels()
            viewModel.init(name, oauth, tryReconnect = !isChangingConfigurations, channels = channels)
            binder.service.checkForNotification()
        }

        override fun onServiceDisconnected(className: ComponentName?) {
            notificationService = null
            isBound = false
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        const val OPEN_CHANNEL_KEY = "open_channel"
    }
}