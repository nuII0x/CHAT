package com.null0x.chat.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.null0x.chat.BuildConfig
import java.io.File
import java.security.MessageDigest

internal object AppUpdateVerifier {

    fun verifyArchive(context: Context, apkFile: File, expectedVersionCode: Int): Result<Unit> {
        return runCatching {
            val packageManager = context.packageManager
            val archive = archivePackageInfo(packageManager, apkFile.absolutePath)
                ?: error("APK de atualizacao invalido")
            val installed = installedPackageInfo(packageManager, context.packageName)
                ?: error("Instalacao atual nao encontrada")

            require(archive.packageName == BuildConfig.APPLICATION_ID) {
                "APK pertence a outro aplicativo"
            }
            require(versionCode(archive) == expectedVersionCode.toLong()) {
                "Versao do APK difere do manifesto"
            }

            val installedCertificates = certificateDigests(installed, includeHistory = true)
            val archiveCertificates = certificateDigests(archive, includeHistory = false)
            require(installedCertificates.isNotEmpty() && archiveCertificates.isNotEmpty()) {
                "Certificado de assinatura ausente"
            }
            require(archiveCertificates.any(installedCertificates::contains)) {
                "APK assinado por certificado diferente"
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun archivePackageInfo(packageManager: PackageManager, archivePath: String): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                archivePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageArchiveInfo(archivePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            packageManager.getPackageArchiveInfo(archivePath, PackageManager.GET_SIGNATURES)
        }
    }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(packageManager: PackageManager, packageName: String): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }
    }

    @Suppress("DEPRECATION")
    private fun certificateDigests(info: PackageInfo, includeHistory: Boolean): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (includeHistory && !signingInfo.hasMultipleSigners()) {
                signingInfo.signingCertificateHistory
            } else {
                signingInfo.apkContentsSigners
            }
        } else {
            info.signatures
        }
        return signatures.orEmpty().mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }
}
