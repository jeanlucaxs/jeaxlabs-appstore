package br.com.jeaxlabs.os

import android.app.PackageInstaller
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

class JxaInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_SUCCESS -> {
                val format = intent.getStringExtra("package_format") ?: "JXA"
                Toast.makeText(context, if (format == "JXU") "Atualização do JeaxLabs OS instalada." else "Instalação do pacote .jxa concluída.", Toast.LENGTH_LONG).show()
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
