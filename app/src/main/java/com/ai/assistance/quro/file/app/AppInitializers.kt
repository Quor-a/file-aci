/*
 * Copyright (c) 2020 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.app

import android.os.AsyncTask
import android.os.Build
import android.webkit.WebView
import jcifs.context.SingletonContext
import com.ai.assistance.quro.file.BuildConfig
import com.ai.assistance.quro.file.coil.initializeCoil
import com.ai.assistance.quro.file.filejob.fileJobNotificationTemplate
import com.ai.assistance.quro.file.ftpserver.ftpServerServiceNotificationTemplate
import com.ai.assistance.quro.file.hiddenapi.HiddenApi
import com.ai.assistance.quro.file.provider.FileSystemProviders
import com.ai.assistance.quro.file.settings.Settings
import com.ai.assistance.quro.file.storage.FtpServerAuthenticator
import com.ai.assistance.quro.file.storage.SftpServerAuthenticator
import com.ai.assistance.quro.file.storage.SmbServerAuthenticator
import com.ai.assistance.quro.file.storage.StorageVolumeListLiveData
import com.ai.assistance.quro.file.storage.WebDavServerAuthenticator
import com.ai.assistance.quro.file.theme.custom.CustomThemeHelper
import com.ai.assistance.quro.file.theme.night.NightModeHelper
import java.util.Properties
import com.ai.assistance.quro.file.provider.ftp.client.Client as FtpClient
import com.ai.assistance.quro.file.provider.sftp.client.Client as SftpClient
import com.ai.assistance.quro.file.provider.smb.client.Client as SmbClient
import com.ai.assistance.quro.file.provider.webdav.client.Client as WebDavClient

val appInitializers = listOf(
    ::initializeCrashlytics,
    ::disableHiddenApiChecks,
    ::initializeWebViewDebugging,
    ::initializeCoil,
    ::initializeFileSystemProviders,
    ::upgradeApp,
    ::initializeLiveDataObjects,
    ::initializeCustomTheme,
    ::initializeNightMode,
    ::createNotificationChannels
)

private fun initializeCrashlytics() {
//#ifdef NONFREE
    com.ai.assistance.quro.file.nonfree.CrashlyticsInitializer.initialize()
//#endif
}

private fun disableHiddenApiChecks() {
    HiddenApi.disableHiddenApiChecks()
}

private fun initializeWebViewDebugging() {
    if (BuildConfig.DEBUG) {
        WebView.setWebContentsDebuggingEnabled(true)
    }
}

private fun initializeFileSystemProviders() {
    FileSystemProviders.install()
    FileSystemProviders.overflowWatchEvents = true
    // SingletonContext.init() calls NameServiceClientImpl.initCache() which connects to network.
    AsyncTask.THREAD_POOL_EXECUTOR.execute {
        SingletonContext.init(
            Properties().apply {
                setProperty("jcifs.netbios.cachePolicy", "0")
                setProperty("jcifs.smb.client.maxVersion", "SMB1")
            }
        )
    }
    FtpClient.authenticator = FtpServerAuthenticator
    SftpClient.authenticator = SftpServerAuthenticator
    SmbClient.authenticator = SmbServerAuthenticator
    WebDavClient.authenticator = WebDavServerAuthenticator
}

private fun initializeLiveDataObjects() {
    // Force initialization of LiveData objects so that it won't happen on a background thread.
    StorageVolumeListLiveData.value
    Settings.FILE_LIST_DEFAULT_DIRECTORY.value
}

private fun initializeCustomTheme() {
    CustomThemeHelper.initialize(application)
}

private fun initializeNightMode() {
    NightModeHelper.initialize(application)
}

private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        notificationManager.createNotificationChannels(
            listOf(
                backgroundActivityStartNotificationTemplate.channelTemplate,
                fileJobNotificationTemplate.channelTemplate,
                ftpServerServiceNotificationTemplate.channelTemplate
            ).map { it.create(application) }
        )
    }
}
