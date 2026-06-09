package com.example.sharelink.adb

import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Client that connects to an Android TV over ADB (TCP) and sends commands.
 * Uses the dadb library which implements the ADB protocol directly —
 * no adb binary or adb server needed on the phone.
 */
class AdbTvClient(private val filesDir: File) {

    private val privateKeyFile = File(filesDir, "adbkey")
    private val publicKeyFile = File(filesDir, "adbkey.pub")

    /**
     * Gets or generates the ADB key pair used for authentication.
     * Uses dadb's own generate() to ensure the format is correct.
     * Keys are stored in the app's private files directory so the TV
     * only needs to authorize once.
     */
    private fun getOrCreateKeyPair(): AdbKeyPair {
        // Self-healing: If the public key file was written in the old incompatible X.509 format
        // (which is ~411 bytes, compared to the correct ADB format which is ~711 bytes),
        // we delete both key files to force a clean, compatible regeneration.
        if (publicKeyFile.exists() && publicKeyFile.length() < 500) {
            privateKeyFile.delete()
            publicKeyFile.delete()
        }

        if (!privateKeyFile.exists() || !publicKeyFile.exists()) {
            AdbKeyPair.generate(privateKeyFile, publicKeyFile)
        }
        return AdbKeyPair.read(privateKeyFile, publicKeyFile)
    }

    /**
     * Opens a URL on the TV by sending an ADB shell command.
     *
     * @param host The TV's IP address on the local network
     * @param port The ADB port (default 5555)
     * @param url The URL to open in the TV's browser
     * @return Result containing the shell output or an error
     */
    suspend fun openUrlOnTv(
        host: String,
        port: Int = DEFAULT_PORT,
        url: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val keyPair = getOrCreateKeyPair()
            Dadb.create(host, port, keyPair).use { dadb ->
                val response = dadb.shell("am start -a android.intent.action.VIEW -d \"$url\"")
                if (response.exitCode != 0) {
                    error("ADB command failed (exit ${response.exitCode}): ${response.errorOutput}")
                }
                response.output
            }
        }
    }

    /**
     * Tests connectivity to the TV by running a simple shell command.
     *
     * @param host The TV's IP address
     * @param port The ADB port (default 5555)
     * @return Result with "ok" on success or an error
     */
    suspend fun testConnection(
        host: String,
        port: Int = DEFAULT_PORT
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val keyPair = getOrCreateKeyPair()
            Dadb.create(host, port, keyPair).use { dadb ->
                val response = dadb.shell("echo ok")
                if (response.exitCode != 0) {
                    error("Connection test failed: ${response.errorOutput}")
                }
                response.output.trim()
            }
        }
    }

    /**
     * Pushes a local file to the TV and attempts to open or install it.
     *
     * @param host The TV's IP address
     * @param port The ADB port (default 5555)
     * @param localFile The local file on the phone to push
     * @param remoteFileName The name of the file on the TV
     * @param mimeType Optional MIME type of the file
     * @return Result containing shell command execution output or error
     */
    suspend fun pushFileAndOpen(
        host: String,
        port: Int = DEFAULT_PORT,
        localFile: File,
        remoteFileName: String,
        mimeType: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val keyPair = getOrCreateKeyPair()
            Dadb.create(host, port, keyPair).use { dadb ->
                val remotePath = "/sdcard/Download/$remoteFileName"
                
                // Push the file
                dadb.push(localFile, remotePath)
                
                // Determine open command based on file type/extension
                val command = when {
                    remoteFileName.endsWith(".apk", ignoreCase = true) || 
                    mimeType == "application/vnd.android.package-archive" -> {
                        "pm install -r \"$remotePath\""
                    }
                    !mimeType.isNullOrBlank() -> {
                        "am start -a android.intent.action.VIEW -d \"file://$remotePath\" -t \"$mimeType\""
                    }
                    else -> {
                        "am start -a android.intent.action.VIEW -d \"file://$remotePath\""
                    }
                }
                
                // Execute command
                val response = dadb.shell(command)
                if (response.exitCode != 0) {
                    error("File pushed, but failed to execute command (exit ${response.exitCode}): ${response.errorOutput}")
                }
                
                response.output
            }
        }
    }

    companion object {
        const val DEFAULT_PORT = 5555
    }
}
