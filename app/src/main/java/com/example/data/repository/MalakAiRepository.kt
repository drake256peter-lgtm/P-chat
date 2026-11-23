package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "MalakAiRepository"

enum class MalakMode(val id: String, val label: String, val hint: String) {
    CHAT("chat", "Chat", "Ask MALAK AI anything..."),
    MAKER("maker", "Make Cool Stuff", "Describe a game, invention, DIY craft, or cool project..."),
    CODE_LAB("code_lab", "Code & Apps", "Describe an app screen, mini-game, script, or tool..."),
    SONG_STORY("song_story", "Songs & Stories", "Give a vibe, genre, or plot for lyrics or a story..."),
    QUIZ_LAB("quiz_lab", "Quiz Maker", "Enter any topic to build an interactive quiz..."),
    SOCIAL_CREATOR("social_creator", "Viral Creator", "Topic for P-CHAT bios, hooks, reels, or Moments..."),
    HOMEWORK("homework", "Homework", "Paste a math, science, or essay question..."),
    IDEAS("ideas", "Ideas", "What do you want to brainstorm?"),
    WRITER("writer", "Write Msg", "Describe the message or reply you want to write..."),
    EXPLAIN("explain", "Explain", "What complex topic should MALAK simplify?"),
    RECOMMEND("recommend", "Recommend", "Ask for movies, music, books, or spots...")
}

enum class StudioArtStyle(val id: String, val label: String, val promptDirective: String) {
    NONE("none", "Freeform", ""),
    CYBERPUNK_3D("cyberpunk_3d", "Cyberpunk 3D", "Unreal Engine 5 3D render, glowing neon-blue and purple rim lighting, sleek futuristic cybernetic details, obsidian dark backdrop"),
    NEON_ANIME("neon_anime", "Neon Anime", "high-detail modern anime key visual, vibrant electric cyan and violet highlights, dynamic cinematic framing"),
    HOLO_POSTER("holo_poster", "Holo Poster", "bold graphic poster design, holographic foil sheen, crisp futuristic typography energy, ultraviolet and cyan neon glow"),
    PIXEL_RETRO("pixel_retro", "Pixel Art", "crisp 16-bit synthwave pixel art diorama, glowing neon sign reflections, clean pixel grid"),
    STUDIO_PORTRAIT("studio_portrait", "Studio Light", "ultra-sharp editorial studio photography, dramatic purple-blue split gel lighting, bokeh depth of field"),
    ISOMETRIC_3D("isometric_3d", "Isometric World", "cute ultra-detailed 3D isometric miniature diorama, glowing neon pathways, clean clay and glass materials")
}

fun buildStyledImagePrompt(userPrompt: String, style: StudioArtStyle): String {
    val clean = userPrompt.trim()
    if (clean.isEmpty()) return ""
    return if (style == StudioArtStyle.NONE || style.promptDirective.isBlank()) {
        clean
    } else {
        "$clean. Visual style: ${style.promptDirective}."
    }
}

class MalakAiRepository {

    private val textModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    You are MALAK AI, the upgraded creative partner and builder built into P-CHAT ("Chat • Connect • Explore").
                    Your personality is smart, bold, imaginative, concise, futuristic, and genuinely helpful.
                    You help users MAKE COOL THINGS as well as solve everyday tasks:
                    - Designing original mini-games, interactive challenges, inventions, and step-by-step DIY builds
                    - Writing clean, runnable code (Kotlin, Compose, HTML/JS, Python) and app blueprints
                    - Crafting memorable song lyrics, hooks, rap bars, short stories, and video scripts
                    - Building interactive quizzes with numbered questions and a clear answer key
                    - Creating punchy P-CHAT bios, captions, hooks, and visual art concepts
                    - Explaining homework step-by-step, drafting messages, simplifying complex topics, and curating recommendations
                    Deliver complete, ready-to-use creations formatted cleanly for mobile screens, and never fabricate personal user data.
                    """.trimIndent()
                )
            }
        )
    }

    private val imageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    @OptIn(PublicPreviewAPI::class)
    private val liveModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = "gemini-3.8-live",
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(voice = Voice("AOEDE"))
            },
            systemInstruction = content {
                text(
                    "You are MALAK AI inside P-CHAT. Speak naturally, warmly, and concisely in real-time voice conversations. Help users brainstorm, invent cool projects, learn, draft messages, or chat."
                )
            }
        )
    }

    fun streamMalakResponse(
        prompt: String,
        mode: MalakMode,
        history: List<Pair<String, String>>,
        attachedBitmap: Bitmap? = null
    ): Flow<String> = flow {
        val modePrefix = when (mode) {
            MalakMode.CHAT -> ""
            MalakMode.MAKER -> "[Creative Maker Mode — design a concrete, imaginative, ready-to-build or ready-to-play creation with clear steps/mechanics] "
            MalakMode.CODE_LAB -> "[Code & App Builder Mode — produce clean, working, well-commented code or architecture] "
            MalakMode.SONG_STORY -> "[Songwriter & Story Studio Mode — craft vivid, rhythmic lyrics or gripping short fiction with structure] "
            MalakMode.QUIZ_LAB -> "[Interactive Quiz Maker Mode — generate 5 engaging questions with options A-D plus an Answer Key at the bottom] "
            MalakMode.SOCIAL_CREATOR -> "[Viral P-CHAT Creator Mode — craft 3 punchy hooks/captions plus a photo/video shot idea] "
            MalakMode.HOMEWORK -> "[Homework Helper Mode — explain step-by-step clearly] "
            MalakMode.IDEAS -> "[Idea Generator Mode — provide crisp, creative options] "
            MalakMode.WRITER -> "[Message Writer Mode — provide ready-to-send message drafts] "
            MalakMode.EXPLAIN -> "[Topic Explainer Mode — use clear analogies and simple terms] "
            MalakMode.RECOMMEND -> "[Recommendations Mode — give curated top picks with short reasons] "
        }

        val fullPrompt = "$modePrefix$prompt"

        withTimeout(60_000L) {
            if (attachedBitmap != null) {
                val inputContent = content {
                    image(attachedBitmap)
                    text(fullPrompt)
                }
                textModel.generateContentStream(inputContent).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) emit(piece)
                }
            } else {
                val chatHistory: List<Content> = history.takeLast(12).map { (role, msg) ->
                    content(role) { text(msg) }
                }
                val chat = textModel.startChat(history = chatHistory)
                chat.sendMessageStream(fullPrompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) emit(piece)
                }
            }
        }
    }

    suspend fun generateImage(prompt: String): Bitmap {
        return try {
            withTimeout(60_000L) {
                val response = imageModel.generateContent(prompt)
                val imagePart = response.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()
                imagePart?.image
                    ?: throw IllegalStateException("No image was returned by gemini-nano-banana-2.1.")
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("Image generation timed out after 60 seconds. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            throw e
        }
    }

    suspend fun editImage(inputBitmap: Bitmap, editInstruction: String): Bitmap {
        return try {
            withTimeout(60_000L) {
                val response = imageModel.generateContent(
                    content {
                        image(inputBitmap)
                        text(editInstruction)
                    }
                )
                val imagePart = response.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()
                imagePart?.image
                    ?: throw IllegalStateException("No edited image was returned by gemini-nano-banana-2.1.")
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("Image editing timed out after 60 seconds. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image editing failed", e)
            throw e
        }
    }

    @OptIn(PublicPreviewAPI::class)
    suspend fun connectLiveSession(): LiveSession {
        return withTimeout(30_000L) {
            liveModel.connect()
        }
    }
}
