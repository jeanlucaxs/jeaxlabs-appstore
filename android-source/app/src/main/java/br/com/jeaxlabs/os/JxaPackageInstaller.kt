package br.com.jeaxlabs.os

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.io.FileInputStream

object JxaPackageInstaller {
    fun install(context: Context, verified: VerifiedJxaPackage, deviceOwner: Boolean) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(
                if (deviceOwner) PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                else PackageInstaller.SessionParams.USER_ACTION_REQUIRED
            )
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                FileInputStream(verified.apkFile).use { input ->
                    session.openWrite("jeaxlabs-jxa-base.apk", 0, verified.apkFile.length()).use { output ->
                        input.copyTo(output, 64 * 1024)
                        session.fsync(output)
                    }
                }
                val resultIntent = Intent(context, JxaInstallStatusReceiver::class.java)
                    .setAction("br.com.jeaxlabs.os.JXA_INSTALL_STATUS")
                    .putExtra("app_name", verified.appName)
                    .putExtra("app_version", verified.version)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val callback = PendingIntent.getBroadcast(context, sessionId, resultIntent, flags)
                session.commit(callback.intentSender)
            }
        } catch (error: Exception) {
            try { installer.abandonSession(sessionId) } catch (_: Exception) { }
            throw error
        } finally {
            verified.apkFile.delete()
        }
    }
}
