package com.null0x.chat.security.identity

enum class MnemonicLanguage(
    val label: String,
    val assetPath: String
) {
    ENGLISH("Inglês", "mnemonic/bip39_english.txt"),
    PORTUGUESE("Português", "mnemonic/bip39_portuguese.txt"),
    SPANISH("Espanhol", "mnemonic/bip39_spanish.txt")
}
