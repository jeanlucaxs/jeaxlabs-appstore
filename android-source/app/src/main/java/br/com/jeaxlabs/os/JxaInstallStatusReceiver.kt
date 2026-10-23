package br.com.jeaxlabs.os

import android.content.pm.PackageInstaller
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

class JxaInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_SUCCESS -> {
                val format = intent.getStringExtra("package_format") ?: "JXA"
                if (format == "JXA") {
                    val appPackage = intent.getStringExtra("app_package_name").orEmpty()
                    if (appPackage.isNotBlank() && appPackage != context.packageName) {
                        val prefs = context.getSharedPreferences("jeax_system", Context.MODE_PRIVATE)
                        val approved = prefs.getStringSet("jxa_allowed_packages", emptySet()).orEmpty().toMutableSet()
                        approved.add(appPackage)
                        prefs.edit().putStringSet("jxa_allowed_packages", approved).apply()
                    }
                }
                Toast.makeText(context, if (format == "JXU") "Atualização do JeaxLabs OS instalada." else "Instalação concluída.", Toast.LENGTH_LONG).show()
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                }
                if (confirmation != null) {
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirmation)
                    } catch (_: Exception) {
                        Toast.makeText(
                            context,
                            "O Android precisa confirmar a instalação. Abra o JeaxLabs OS fora do modo bloqueado para concluir.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(context, "O Android solicitou confirmação para instalar o pacote.", Toast.LENGTH_LONG).show()
                }
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.take(150)
                    .orEmpty()
                Toast.makeText(
                    context,
                    if (message.isBlank()) "O Android recusou a instalação do pacote." else "Instalação recusada: $message",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
