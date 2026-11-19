package com.example.data

import com.example.model.LanguageOption
import com.example.model.VocalProfile
import com.example.model.VoiceGender
import com.example.model.VoiceStyleType

object VoiceCatalog {

    val languages: List<LanguageOption> = listOf(
        LanguageOption(
            tag = "hi-IN",
            shortCode = "HI",
            displayName = "Hindi",
            nativeName = "हिन्दी",
            sampleText = "नमस्ते! वानीवॉक्स में आपका स्वागत है। यह ऑफ़लाइन मोड में भी बिल्कुल स्वाभाविक और मधुर आवाज़ में बोलता है।",
            isQuickPin = true
        ),
        LanguageOption(
            tag = "en-IN",
            shortCode = "EN·IN",
            displayName = "English (India)",
            nativeName = "English (IN)",
            sampleText = "Welcome to VaniVox. Switch seamlessly between natural vocal profiles, listen offline with low battery usage, or export crisp audio.",
            isQuickPin = true
        ),
        LanguageOption(
            tag = "en-US",
            shortCode = "EN·US",
            displayName = "English (US)",
            nativeName = "English (US)",
            sampleText = "Natural speech starts with balanced pacing, clear breathing pauses, and expressive tone that feels effortlessly human.",
            isQuickPin = true
        ),
        LanguageOption(
            tag = "en-GB",
            shortCode = "EN·UK",
            displayName = "English (UK)",
            nativeName = "English (UK)",
            sampleText = "Good evening. This studio profile delivers warm, articulate narration suitable for audiobooks and calm listening.",
            isQuickPin = true
        ),
        LanguageOption(
            tag = "bn-IN",
            shortCode = "BN",
            displayName = "Bengali",
            nativeName = "বাংলা",
            sampleText = "নমস্কার! ভ্যানিভক্স স্টুডিওতে স্বাগতম। এখন অফলাইনেও পরিষ্কার ও স্বাভাবিক কণ্ঠস্বর শুনুন।",
            isQuickPin = true
        ),
        LanguageOption(
            tag = "ta-IN",
            shortCode = "TA",
            displayName = "Tamil",
            nativeName = "தமிழ்",
            sampleText = "வணக்கம்! வானிவாக்ஸ் உரை-க்கு-பேச்சு செயலிக்கு வரவேற்கிறோம். இயல்பான குரலில் தெளிவாகக் கேளுங்கள்.",
            isQuickPin = false
        ),
        LanguageOption(
            tag = "te-IN",
            shortCode = "TE",
            displayName = "Telugu",
            nativeName = "తెలుగు",
            sampleText = "నమస్కారం! వాణీవాక్స్‌కు స్వాగతం. సహజమైన స్వరంతో ఆఫ్‌లైన్ లో కూడా వినండి.",
            isQuickPin = false
        ),
        LanguageOption(
            tag = "mr-IN",
            shortCode = "MR",
            displayName = "Marathi",
            nativeName = "मराठी",
            sampleText = "नमस्कार! वाणीवॉक्स स्टुडिओमध्ये आपले स्वागत आहे. नैसर्गिक आवाजात मजकूर ऐका.",
            isQuickPin = false
        ),
        LanguageOption(
            tag = "es-ES",
            shortCode = "ES",
            displayName = "Spanish",
            nativeName = "Español",
            sampleText = "Hola y bienvenido a VaniVox. Disfruta de una voz natural y fluida incluso sin conexión a internet.",
            isQuickPin = true
        ),
        LanguageOption(
            tag = "fr-FR",
            shortCode = "FR",
            displayName = "French",
            nativeName = "Français",
            sampleText = "Bonjour et bienvenue dans VaniVox. Changez de langue et de style vocal en un instant.",
            isQuickPin = false
        ),
        LanguageOption(
            tag = "de-DE",
            shortCode = "DE",
            displayName = "German",
            nativeName = "Deutsch",
            sampleText = "Willkommen bei VaniVox. Klare, natürliche Sprachausgabe mit geringem Akkuverbrauch im Offline-Modus.",
            isQuickPin = false
        ),
        LanguageOption(
            tag = "ja-JP",
            shortCode = "JA",
            displayName = "Japanese",
            nativeName = "日本語",
            sampleText = "こんにちは、VaniVoxへようこそ。自然な音声スタイルを簡単に切り替えられます。",
            isQuickPin = false
        ),
        LanguageOption(
            tag = "ar-SA",
            shortCode = "AR",
            displayName = "Arabic",
            nativeName = "العربية",
            sampleText = "مرحباً بكم في فاني فوكس. استمتع بصوت طبيعي وواضح حتى في وضع عدم الاتصال.",
            isQuickPin = false
        )
    )

    val vocalProfiles: List<VocalProfile> = listOf(
        // Female styles
        VocalProfile(
            id = "female_sweet",
            displayName = "Meera",
            gender = VoiceGender.FEMALE,
            styleType = VoiceStyleType.SWEET,
            tagline = "Sweet & melodic tone with gentle conversational flow",
            pitchMultiplier = 1.07f,
            rateMultiplier = 0.96f,
            naturalPauseMs = 240L
        ),
        VocalProfile(
            id = "female_soft",
            displayName = "Ananya",
            gender = VoiceGender.FEMALE,
            styleType = VoiceStyleType.SOFT,
            tagline = "Soft, soothing & airy delivery for calm reading",
            pitchMultiplier = 1.02f,
            rateMultiplier = 0.88f,
            naturalPauseMs = 310L
        ),
        VocalProfile(
            id = "female_hard",
            displayName = "Kavya",
            gender = VoiceGender.FEMALE,
            styleType = VoiceStyleType.HARD,
            tagline = "Bold, crisp & assertive broadcast articulation",
            pitchMultiplier = 0.94f,
            rateMultiplier = 1.04f,
            naturalPauseMs = 160L
        ),
        VocalProfile(
            id = "female_warm",
            displayName = "Diya",
            gender = VoiceGender.FEMALE,
            styleType = VoiceStyleType.WARM,
            tagline = "Expressive audiobook storyteller cadence",
            pitchMultiplier = 0.99f,
            rateMultiplier = 0.93f,
            naturalPauseMs = 280L
        ),

        // Male styles
        VocalProfile(
            id = "male_soft",
            displayName = "Aarav",
            gender = VoiceGender.MALE,
            styleType = VoiceStyleType.SOFT,
            tagline = "Calm, soft-spoken & relaxed study voice",
            pitchMultiplier = 0.88f,
            rateMultiplier = 0.90f,
            naturalPauseMs = 300L
        ),
        VocalProfile(
            id = "male_sweet",
            displayName = "Rohan",
            gender = VoiceGender.MALE,
            styleType = VoiceStyleType.SWEET,
            tagline = "Warm, friendly & pleasant everyday speaker",
            pitchMultiplier = 0.93f,
            rateMultiplier = 0.97f,
            naturalPauseMs = 230L
        ),
        VocalProfile(
            id = "male_hard",
            displayName = "Vikram",
            gender = VoiceGender.MALE,
            styleType = VoiceStyleType.HARD,
            tagline = "Deep, commanding & authoritative studio bass",
            pitchMultiplier = 0.78f,
            rateMultiplier = 1.02f,
            naturalPauseMs = 170L
        ),
        VocalProfile(
            id = "male_crisp",
            displayName = "Kabir",
            gender = VoiceGender.MALE,
            styleType = VoiceStyleType.CRISP,
            tagline = "Balanced, clear & natural podcast narrator",
            pitchMultiplier = 0.85f,
            rateMultiplier = 1.00f,
            naturalPauseMs = 220L
        )
    )

    fun languageForTag(tag: String): LanguageOption {
        return languages.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
            ?: languages.first()
    }

    fun profileForId(id: String): VocalProfile {
        return vocalProfiles.firstOrNull { it.id == id }
            ?: vocalProfiles.first()
    }
}
