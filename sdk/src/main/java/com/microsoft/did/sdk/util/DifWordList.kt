// Copyright (c) Microsoft Corporation. All rights reserved

package com.microsoft.did.sdk.util

import android.content.Context
import android.content.res.AssetManager
import java.io.InputStreamReader
import java.security.SecureRandom
import java.util.Locale
import java.util.regex.Pattern

object DifWordList {
    lateinit var wordList: List<String>

    fun initialize(context: Context) {
        val reader = InputStreamReader(context.assets.open("difwordlist.txt", AssetManager.ACCESS_STREAMING), "UTF-8")
        val words = reader.readLines()
        reader.close()
        wordList = words
    }

    fun generateDifPassword(): String {
        val random = SecureRandom()
        val wordSet = linkedSetOf<String>()
        for (index in 0 until Constants.PASSWORD_SET_SIZE) {
            var wordIndex: Int
            var word: String
            do {
                wordIndex = random.nextInt(wordList.count())
                word = wordList[wordIndex]
            } while (wordSet.contains(word))
            wordSet.add(word)
        }
        return wordSet.joinToString(" ")
    }

    fun normalizePassword(password: String): String {
        return password.split(Pattern.compile("\\s+")).filter { it.isNotBlank() }.joinToString(" ") { it.toLowerCase(Locale.ENGLISH) }
    }
}