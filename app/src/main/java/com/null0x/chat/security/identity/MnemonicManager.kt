package com.null0x.chat.security.identity

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class MnemonicManager(
    private val wordList: List<String>
) {

    companion object {
        private const val WORD_COUNT = 12
        private const val BITS_PER_WORD = 11
        private const val ENTROPY_BYTES = 16
        private const val BIP39_PBKDF2_ITERATIONS = 2048
        private const val BIP39_PBKDF2_BITS = 512

        fun fromAssets(context: Context, assetPath: String = "mnemonic/bip39_english.txt"): MnemonicManager {
            val words = context.assets.open(assetPath).use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                    reader.readLines().map { it.trim() }.filter { it.isNotBlank() }
                }
            }
            return MnemonicManager(words)
        }
    }

    private val random = SecureRandom()
    private val wordIndex = wordList.withIndex().associate { it.value to it.index }

    init {
        require(wordList.size == 2048) {
            "A wordlist BIP-39 precisa ter 2048 palavras"
        }
    }

    fun generate12Words(): String {
        val entropy = ByteArray(ENTROPY_BYTES).also { random.nextBytes(it) }
        val indexes = mnemonicIndicesFromEntropy(entropy)
        return indexes.joinToString(" ") { index -> wordList[index] }
    }

    fun validateMnemonic(mnemonic: String): Boolean {
        return runCatching {
            mnemonicEntropy(mnemonic)
            true
        }.getOrDefault(false)
    }

    fun seedFromMnemonic(mnemonic: String, passphrase: String = ""): ByteArray {
        require(validateMnemonic(mnemonic)) {
            "Mnemonic inválida"
        }
        val normalizedMnemonic = normalize(mnemonic)
        val normalizedPassphrase = normalize(passphrase)
        val salt = "mnemonic$normalizedPassphrase".toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(
            normalizedMnemonic.toCharArray(),
            salt,
            BIP39_PBKDF2_ITERATIONS,
            BIP39_PBKDF2_BITS
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        return factory.generateSecret(spec).encoded
    }

    fun mnemonicEntropy(mnemonic: String): ByteArray {
        val words = tokenize(mnemonic)
        require(words.size == WORD_COUNT) {
            "A mnemonic deve ter exatamente 12 palavras"
        }
        val indexes = words.map { word ->
            wordIndex[word] ?: throw IllegalArgumentException("Palavra inválida na mnemonic")
        }
        val bits = indexes.flatMap { index -> index.toBits(BITS_PER_WORD) }
        val entropyBitsLength = ENTROPY_BYTES * 8
        val checksumBitsLength = entropyBitsLength / 32
        val entropyBits = bits.take(entropyBitsLength)
        val checksumBits = bits.takeLast(checksumBitsLength)
        val entropy = entropyBits.toByteArrayBits()
        val expectedChecksum = sha256(entropy).toBits().take(checksumBitsLength)
        require(expectedChecksum == checksumBits) {
            "Checksum da mnemonic inválido"
        }
        return entropy
    }

    private fun mnemonicIndicesFromEntropy(entropy: ByteArray): List<Int> {
        require(entropy.size == ENTROPY_BYTES) {
            "A entropy precisa ter ${ENTROPY_BYTES} bytes"
        }
        val entropyBits = entropy.toBits()
        val checksumLength = entropyBits.size / 32
        val checksumBits = sha256(entropy).toBits().take(checksumLength)
        val allBits = entropyBits + checksumBits
        return allBits.chunked(BITS_PER_WORD) { bits ->
            bits.fold(0) { acc, bit -> (acc shl 1) or bit }
        }
    }

    private fun tokenize(mnemonic: String): List<String> {
        return normalize(mnemonic)
            .lowercase(Locale.ROOT)
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
    }

    private fun sha256(input: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(input)
    }

    private fun Int.toBits(bitCount: Int): List<Int> {
        return (bitCount - 1 downTo 0).map { shift -> (this shr shift) and 1 }
    }

    private fun ByteArray.toBits(): List<Int> {
        return flatMap { byte ->
            val unsigned = byte.toInt() and 0xFF
            (7 downTo 0).map { shift -> (unsigned shr shift) and 1 }
        }
    }

    private fun List<Int>.toByteArrayBits(): ByteArray {
        require(size % 8 == 0) {
            "Bit count inválido"
        }
        return chunked(8).map { bits ->
            bits.fold(0) { acc, bit -> (acc shl 1) or bit }.toByte()
        }.toByteArray()
    }
}
