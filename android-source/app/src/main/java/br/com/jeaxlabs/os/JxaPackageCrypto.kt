package br.com.jeaxlabs.os

import android.content.Context
import android.os.Build
import android.util.Base64
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import java.util.zip.ZipFile
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class VerifiedJxaPackage(
    val apkFile: File,
    val appName: String,
    val version: String,
    val packageFormat: String = "JXA",
    val targetPackage: String? = null,
    val versionCode: Long = 0L,
    val minimumVersion: String = "",
    val releaseNotes: String = ""
)

object JxaPackageCrypto {
    private const val FORMAT_VERSION = 1
    private const val KDF_ROUNDS = 600_000
    private const val MAX_PACKAGE_BYTES = 512L * 1024L * 1024L
    private const val MAX_MANIFEST_BYTES = 1024L * 1024L
    private const val MAX_PUBLIC_KEY_BYTES = 16L * 1024L
    private const val MAX_SIGNATURE_BYTES = 1024L

    fun downloadAndDecrypt(
        context: Context,
        packageUrl: String,
        packagePassword: String,
        trustedFingerprint: String,
        expectedFormat: String = "JXA"
    ): VerifiedJxaPackage {
        require(packageUrl.startsWith("https://", true)) {
            "A URL do pacote precisa usar HTTPS."
        }
        require(packagePassword.isNotEmpty()) {
            "Digite a senha de criptografia do pacote."
        }
        val normalizedFingerprint = trustedFingerprint
            .replace(":", "")
            .replace(" ", "")
            .trim()
            .lowercase(Locale.ROOT)
        require(Regex("^[a-f0-9]{64}$").matches(normalizedFingerprint)) {
            "Configure o fingerprint SHA-256 confiável do publicador antes de instalar."
        }

        val packageFile = File.createTempFile("jeax_jxa_", ".jxa", context.cacheDir)
        try {
            downloadHttps(packageUrl.trim(), packageFile)
            return decryptPackage(context, packageFile, packagePassword, normalizedFingerprint, expectedFormat)
        } finally {
            packageFile.delete()
        }
    }

    private fun downloadHttps(address: String, destination: File) {
        val connection = URL(address).openConnection() as? HttpURLConnection
            ?: throw IllegalArgumentException("Não foi possível abrir a conexão HTTPS.")
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "JeaxLabsOS/1.4.1")
            connection.connect()
            require(connection.url.protocol.equals("https", true)) {
                "O servidor redirecionou para uma conexão que não é HTTPS."
            }
            val responseCode = connection.responseCode
            require(responseCode in 200..299) {
                "O servidor respondeu HTTP $responseCode ao baixar o .jxa."
            }
            val contentLength = connection.getHeaderFieldLong("Content-Length", -1L)
            require(contentLength <= MAX_PACKAGE_BYTES || contentLength < 0L) {
                "O pacote excede o limite de 512 MB."
            }
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    copyLimited(input, output, MAX_PACKAGE_BYTES, "download do pacote")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun decryptPackage(
        context: Context,
        packageFile: File,
        packagePassword: String,
        trustedFingerprint: String,
        expectedFormat: String
    ): VerifiedJxaPackage {
        val normalizedFormat = expectedFormat.trim().uppercase(Locale.ROOT)
        require(normalizedFormat == "JXA" || normalizedFormat == "JXU") {
            "Formato interno de pacote não suportado."
        }
        val manifestFile = readPackageEntries(context, packageFile)
        var apkFile: File? = null
        try {
            val manifestBytes = manifestFile.manifestBytes
                ?: throw IllegalArgumentException("O pacote não contém manifest.json.")
            val signature = manifestFile.signature
                ?: throw IllegalArgumentException("O pacote não contém a assinatura Ed25519.")
            val publicPem = manifestFile.publicKeyPem
                ?: throw IllegalArgumentException("O pacote não contém a chave pública do publicador.")
            val encryptedPayload = manifestFile.payloadFile
                ?: throw IllegalArgumentException("O pacote não contém payload.aesgcm.")

            val manifestText = String(manifestBytes, StandardCharsets.UTF_8)
            val manifest = JSONObject(manifestText)
            require(manifest.optString("format") == normalizedFormat) {
                "O arquivo não corresponde ao formato .$normalizedFormat solicitado."
            }
            require(manifest.optInt("format_version", -1) == FORMAT_VERSION) {
                "A versão do formato .jxa não é compatível."
            }
            require(manifest.optString("encryption") == "AES-256-GCM") {
                "O algoritmo de criptografia do pacote não é compatível."
            }
            require(manifest.optString("kdf") == "PBKDF2-HMAC-SHA256") {
                "O método de derivação da chave não é compatível."
            }
            require(manifest.optInt("kdf_iterations", -1) == KDF_ROUNDS) {
                "A quantidade de iterações PBKDF2 não é aceita por esta versão."
            }
            require(manifest.optString("signature") == "Ed25519") {
                "O algoritmo de assinatura do pacote não é compatível."
            }
            require(manifest.optString("payload_filename") == "payload.aesgcm") {
                "O nome do conteúdo criptografado é inválido."
            }

            val publicDer = decodePublicPem(publicPem)
            val actualFingerprint = sha256(publicDer)
            require(actualFingerprint.equals(trustedFingerprint, true)) {
                "Fingerprint do publicador diferente do cadastrado. Pacote recusado."
            }
            verifySignature(publicDer, manifestBytes, signature)

            val expectedPayloadHash = manifest.optString("payload_sha256").lowercase(Locale.ROOT)
            require(Regex("^[a-f0-9]{64}$").matches(expectedPayloadHash)) {
                "O SHA-256 do conteúdo está ausente ou malformado."
            }
            val actualPayloadHash = sha256File(encryptedPayload)
            require(actualPayloadHash == expectedPayloadHash) {
                "O conteúdo criptografado foi alterado ou está corrompido."
            }
            val declaredPayloadSize = manifest.optLong("payload_size", -1L)
            require(declaredPayloadSize == encryptedPayload.length()) {
                "O tamanho do conteúdo não corresponde ao manifesto."
            }

            val version = manifest.optString("version").trim()
            val appName = if (normalizedFormat == "JXA") {
                manifest.optString("name").trim()
            } else {
                manifest.optString("product").trim()
            }
            require(appName.isNotEmpty() && version.isNotEmpty()) {
                "O manifesto não informa o nome e a versão do pacote."
            }

            var targetPackage: String? = null
            var declaredVersionCode = 0L
            var minimumVersion = ""
            var releaseNotes = ""
            if (normalizedFormat == "JXU") {
                require(manifest.optString("package_type") == "system-update") {
                    "O pacote .jxu não é uma atualização do sistema."
                }
                targetPackage = manifest.optString("target_package").trim()
                require(targetPackage == context.packageName) {
                    "Esta atualização não é destinada a este aplicativo."
                }
                minimumVersion = manifest.optString("minimum_version").trim()
                require(isValidVersion(minimumVersion)) {
                    "A versão mínima exigida no .jxu é inválida."
                }
                declaredVersionCode = manifest.optLong("version_code", -1L)
                require(declaredVersionCode > 0L) {
                    "O .jxu não informa um versionCode Android válido."
                }
                releaseNotes = manifest.optString("release_notes", "")
                require(manifest.optString("source_filename").endsWith(".apk", true)) {
                    "A carga de uma atualização do sistema precisa ser um APK."
                }
            }

            val aad = manifest.optString("aad_utf8")
            val expectedAad = if (normalizedFormat == "JXA") {
                "JeaxLabs-JXA-v1|$appName|$version"
            } else {
                "JeaxLabs-JXU-v1|$version|$minimumVersion"
            }
            require(aad == expectedAad) {
                "Os dados autenticados do pacote não correspondem ao manifesto."
            }

            val salt = decodeHex(manifest.optString("salt_hex"), 16, "salt")
            val nonce = decodeHex(manifest.optString("nonce_hex"), 12, "nonce")
            val generator = PKCS5S2ParametersGenerator(SHA256Digest())
            generator.init(packagePassword.toByteArray(StandardCharsets.UTF_8), salt, KDF_ROUNDS)
            val key = (generator.generateDerivedParameters(256) as KeyParameter).key

            apkFile = File.createTempFile("jeax_verified_", ".apk", context.cacheDir)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(key, "AES"),
                    GCMParameterSpec(128, nonce)
                )
                cipher.updateAAD(aad.toByteArray(StandardCharsets.UTF_8))
                FileInputStream(encryptedPayload).use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var totalWritten = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            val plain = cipher.update(buffer, 0, count)
                            if (plain != null && plain.isNotEmpty()) {
                                totalWritten += plain.size
                                require(totalWritten <= MAX_PACKAGE_BYTES) {
                                    "O APK descriptografado excede o limite de 512 MB."
                                }
                                output.write(plain)
                            }
                        }
                        val finalBytes = cipher.doFinal()
                        totalWritten += finalBytes.size
                        require(totalWritten in 1..MAX_PACKAGE_BYTES) {
                            "O APK descriptografado está vazio ou excede 512 MB."
                        }
                        output.write(finalBytes)
                        output.fd.sync()
                    }
                }
                val installedApkInfo = validateApk(context, apkFile)
                if (normalizedFormat == "JXU") {
                    val parsedName = installedApkInfo.versionName ?: ""
                    val parsedCode = if (Build.VERSION.SDK_INT >= 28) installedApkInfo.longVersionCode else installedApkInfo.versionCode.toLong()
                    require(installedApkInfo.packageName == targetPackage) {
                        "O APK interno não atualiza o pacote JeaxLabs OS esperado."
                    }
                    require(parsedName == version && parsedCode == declaredVersionCode) {
                        "A versão do manifesto .jxu não corresponde à versão real do APK."
                    }
                    val current = context.packageManager.getPackageInfo(context.packageName, 0)
                    val currentName = current.versionName ?: "0.0.0"
                    val currentCode = if (Build.VERSION.SDK_INT >= 28) current.longVersionCode else current.versionCode.toLong()
                    require(compareVersions(currentName, minimumVersion) >= 0) {
                        "Esta atualização exige JeaxLabs OS $minimumVersion ou superior."
                    }
                    require(parsedCode > currentCode) {
                        "O .jxu não é uma versão mais nova que a versão instalada."
                    }
                }
            } catch (error: Exception) {
                apkFile.delete()
                throw if (error is IllegalArgumentException || error is SecurityException) error
                else SecurityException("Senha incorreta ou autenticação AES-GCM falhou.", error)
            } finally {
                key.fill(0)
            }

            val result = VerifiedJxaPackage(
                apkFile = apkFile,
                appName = appName,
                version = version,
                packageFormat = normalizedFormat,
                targetPackage = targetPackage,
                versionCode = declaredVersionCode,
                minimumVersion = minimumVersion,
                releaseNotes = releaseNotes
            )
            apkFile = null
            return result
        } finally {
            apkFile?.delete()
            manifestFile.payloadFile?.delete()
        }
    }

    private data class PackageEntries(
        val manifestBytes: ByteArray?,
        val signature: ByteArray?,
        val publicKeyPem: ByteArray?,
        val payloadFile: File?
    )

    private fun readPackageEntries(context: Context, packageFile: File): PackageEntries {
        var manifestBytes: ByteArray? = null
        var signature: ByteArray? = null
        var publicPem: ByteArray? = null
        var payloadFile: File? = null
        val seen = mutableSetOf<String>()
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(packageFile))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when (entry.name) {
                        "manifest.json", "signature.ed25519", "publisher.pub.pem", "payload.aesgcm" -> {
                            require(seen.add(entry.name)) {
                                "O pacote contém uma entrada repetida: ${entry.name}."
                            }
                            when (entry.name) {
                                "manifest.json" -> manifestBytes = readCurrentEntry(zip, MAX_MANIFEST_BYTES, "manifesto")
                                "signature.ed25519" -> signature = readCurrentEntry(zip, MAX_SIGNATURE_BYTES, "assinatura")
                                "publisher.pub.pem" -> publicPem = readCurrentEntry(zip, MAX_PUBLIC_KEY_BYTES, "chave pública")
                                "payload.aesgcm" -> {
                                    payloadFile = File.createTempFile("jeax_cipher_", ".bin", context.cacheDir)
                                    FileOutputStream(payloadFile).use { output ->
                                        copyLimited(zip, output, MAX_PACKAGE_BYTES, "conteúdo criptografado")
                                        output.fd.sync()
                                    }
                                }
                            }
                        }
                        else -> throw IllegalArgumentException("Entrada inesperada no pacote .jxa: " + entry.name)
                    }
                    zip.closeEntry()
                }
            }
            require(seen.containsAll(setOf("manifest.json", "signature.ed25519", "publisher.pub.pem", "payload.aesgcm"))) {
                "O pacote .jxa está incompleto."
            }
            return PackageEntries(manifestBytes, signature, publicPem, payloadFile)
        } catch (error: Exception) {
            payloadFile?.delete()
            throw error
        }
    }

    private fun readCurrentEntry(input: InputStream, maxBytes: Long, description: String): ByteArray {
        val output = ByteArrayOutputStream()
        copyLimited(input, output, maxBytes, description)
        return output.toByteArray()
    }

    private fun copyLimited(input: InputStream, output: java.io.OutputStream, maxBytes: Long, description: String) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "O tamanho excede o limite permitido para $description." }
            output.write(buffer, 0, count)
        }
    }

    private fun decodePublicPem(pemBytes: ByteArray): ByteArray {
        val pem = String(pemBytes, StandardCharsets.US_ASCII).trim()
        require(pem.startsWith("-----BEGIN PUBLIC KEY-----") && pem.endsWith("-----END PUBLIC KEY-----")) {
            "O certificado público do pacote não está em formato PEM válido."
        }
        val body = pem
            .removePrefix("-----BEGIN PUBLIC KEY-----")
            .removeSuffix("-----END PUBLIC KEY-----")
            .filterNot { it.isWhitespace() }
        val der = Base64.decode(body, Base64.DEFAULT)
        val info = SubjectPublicKeyInfo.getInstance(der)
        require(info.algorithm.algorithm.id == "1.3.101.112") {
            "A chave do publicador não é uma chave Ed25519."
        }
        require(info.publicKeyData.bytes.size == 32) {
            "A chave Ed25519 tem tamanho inválido."
        }
        return der
    }

    private fun verifySignature(publicDer: ByteArray, manifest: ByteArray, signature: ByteArray) {
        require(signature.size == 64) { "A assinatura Ed25519 tem tamanho inválido." }
        val info = SubjectPublicKeyInfo.getInstance(publicDer)
        val rawPublicKey = Ed25519PublicKeyParameters(info.publicKeyData.bytes, 0)
        val verifier = Ed25519Signer()
        verifier.init(false, rawPublicKey)
        verifier.update(manifest, 0, manifest.size)
        require(verifier.verifySignature(signature)) {
            "A assinatura do manifesto é inválida. Pacote recusado."
        }
    }

    private fun validateApk(context: Context, file: File): android.content.pm.PackageInfo {
        require(file.isFile && file.length() > 0L) {
            "O conteúdo descriptografado não é um APK válido."
        }
        ZipFile(file).use { apk ->
            require(apk.getEntry("AndroidManifest.xml") != null) {
                "O arquivo descriptografado não contém AndroidManifest.xml."
            }
        }
        return context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw IllegalArgumentException("Android não reconheceu o conteúdo como APK instalável.")
    }

    private fun isValidVersion(value: String): Boolean =
        Regex("^\\d+\\.\\d+\\.\\d+$").matches(value)

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split(".").map { it.toIntOrNull() ?: 0 }
        val b = right.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until 3) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av > bv) return 1
            if (av < bv) return -1
        }
        return 0
    }

    private fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun decodeHex(value: String, expectedBytes: Int, field: String): ByteArray {
        val normalized = value.trim()
        require(normalized.length == expectedBytes * 2 && normalized.matches(Regex("^[a-fA-F0-9]+$"))) {
            "Campo $field inválido no manifesto."
        }
        return ByteArray(expectedBytes) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
