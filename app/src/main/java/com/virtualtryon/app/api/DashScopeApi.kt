package com.virtualtryon.app.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.*

/**
 * Data classes for DashScope qwen-image-2.0 API
 * Uses the messages format for image editing
 */

data class ImageEditRequest(
    val model: String = "qwen-image-2.0",
    val input: InputData,
    val parameters: Parameters? = null
)

data class InputData(
    val messages: List<Message>
)

data class Message(
    val role: String = "user",
    val content: List<ContentItem>
)

data class ContentItem(
    val image: String? = null,
    val text: String? = null
)

data class Parameters(
    val n: Int = 1,
    val negative_prompt: String? = null,
    val size: String? = null
)

data class ImageEditResponse(
    val requestId: String?,
    val output: OutputData?,
    val code: String?,
    val message: String?
)

data class OutputData(
    val choices: List<Choice>?,
    val taskId: String?,
    val taskStatus: String?
)

data class Choice(
    val finish_reason: String?,
    val message: MessageResponse?
)

data class MessageResponse(
    val role: String?,
    val content: List<ContentResponse>?
)

data class ContentResponse(
    val image: String?,
    val text: String?
)

/**
 * Retrofit interface for DashScope API
 */
interface DashScopeApi {
    @POST("services/aigc/multimodal-generation/generation")
    fun generateImage(
        @Header("Authorization") authorization: String,
        @Body request: ImageEditRequest
    ): Call<ImageEditResponse>
}
