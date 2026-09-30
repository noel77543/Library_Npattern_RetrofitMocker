package com.noelsung.retrofitmocker

import com.noelsung.retrofitmocker.annotations.MockRequest
import com.noelsung.retrofitmocker.annotations.MockResponse
import io.reactivex.rxjava3.core.Single
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.lang.reflect.Type
import java.util.concurrent.atomic.AtomicInteger

const val BASE_URL = "https://example.com/"

data class User(val id: String, val name: String)

interface TestApi {
    //未標註 MockRequest，應走真實網路
    @GET("users/{id}")
    suspend fun realUser(@Path("id") id: String): User

    @MockRequest(TestMocker::class, TestMocker.USER)
    @GET("users/{id}")
    suspend fun user(@Path("id") id: String, @Query("lang") lang: String?): User

    @MockRequest(TestMocker::class, TestMocker.USER)
    @GET("users/{id}")
    fun userCall(@Path("id") id: String, @Query("lang") lang: String?): Call<User>

    @MockRequest(TestMocker::class, TestMocker.USER)
    @GET("users/{id}")
    fun userSingle(@Path("id") id: String, @Query("lang") lang: String?): Single<User>

    @MockRequest(TestMocker::class, TestMocker.UNIT)
    @POST("users/{id}/follow")
    suspend fun follow(@Path("id") id: String)

    @MockRequest(TestMocker::class, TestMocker.NOT_FOUND)
    @GET("users/{id}")
    suspend fun notFound(@Path("id") id: String): User

    @MockRequest(TestMocker::class, TestMocker.NOT_FOUND)
    @GET("users/{id}")
    fun notFoundCall(@Path("id") id: String): Call<User>

    @MockRequest(TestMocker::class, TestMocker.NETWORK_ERROR)
    @GET("users/{id}")
    suspend fun networkError(@Path("id") id: String): User

    @MockRequest(TestMocker::class, TestMocker.NETWORK_ERROR)
    @GET("users/{id}")
    fun networkErrorCall(@Path("id") id: String): Call<User>

    @MockRequest(TestMocker::class, TestMocker.SLOW)
    @GET("users/{id}")
    fun slowCall(@Path("id") id: String): Call<User>

    @MockRequest(TestMocker::class, TestMocker.SLOW)
    @GET("users/{id}")
    suspend fun slow(@Path("id") id: String): User

    //延遲遠大於取消所需時間，讓取消相關測試在高負載下仍能明確區分是否被中斷
    @MockRequest(TestMocker::class, TestMocker.CANCELABLE)
    @GET("users/{id}")
    fun cancelableCall(@Path("id") id: String): Call<User>

    @MockRequest(TestMocker::class, TestMocker.DEFAULT_DELAY)
    @GET("users/{id}")
    fun defaultDelayCall(@Path("id") id: String): Call<User>

    @MockRequest(TestMocker::class, TestMocker.NO_DELAY)
    @GET("users/{id}")
    fun noDelayCall(@Path("id") id: String): Call<User>

    @MockRequest(TestMocker::class, "MISSING_TAG")
    @GET("users/{id}")
    suspend fun missingTag(@Path("id") id: String): User

    @MockRequest(TestMocker::class, TestMocker.DUPLICATED)
    @GET("users/{id}")
    suspend fun duplicatedTag(@Path("id") id: String): User
}

class TestMocker {
    companion object {
        const val USER = "USER"
        const val UNIT = "UNIT"
        const val NOT_FOUND = "NOT_FOUND"
        const val NETWORK_ERROR = "NETWORK_ERROR"
        const val SLOW = "SLOW"
        const val SLOW_DELAY_MILLIS = 300L
        const val CANCELABLE = "CANCELABLE"
        const val CANCELABLE_DELAY_MILLIS = 5000L
        const val DEFAULT_DELAY = "DEFAULT_DELAY"
        const val NO_DELAY = "NO_DELAY"
        const val DUPLICATED = "DUPLICATED"

        //mocker 由 factory 以反射建立，因此以 companion 記錄呼叫狀態
        val invocations = AtomicInteger()

        @Volatile
        var lastThreadName: String? = null

        fun reset() {
            invocations.set(0)
            lastThreadName = null
        }

        private fun record() {
            invocations.incrementAndGet()
            lastThreadName = Thread.currentThread().name
        }
    }

    @MockResponse(tag = USER)
    fun user(id: String, lang: String?): User {
        record()
        return User(id, "name-$id-${lang ?: "default"}")
    }

    @MockResponse(tag = UNIT)
    fun follow(id: String) {
        record()
    }

    @MockResponse(tag = NOT_FOUND)
    fun notFound(id: String): Response<User> {
        record()
        return Response.error(
            404,
            ResponseBody.create(MediaType.get("application/json"), """{"message":"$id not found"}"""),
        )
    }

    @MockResponse(tag = NETWORK_ERROR)
    fun networkError(id: String): User {
        record()
        throw IOException("mock network error: $id")
    }

    @MockResponse(tag = SLOW, delayMillis = SLOW_DELAY_MILLIS)
    fun slow(id: String): User {
        record()
        return User(id, "slow")
    }

    @MockResponse(tag = CANCELABLE, delayMillis = CANCELABLE_DELAY_MILLIS)
    fun cancelable(id: String): User {
        record()
        return User(id, "cancelable")
    }

    @MockResponse(tag = DEFAULT_DELAY)
    fun defaultDelay(id: String): User {
        record()
        return User(id, "default-delay")
    }

    @MockResponse(tag = NO_DELAY, delayMillis = 0)
    fun noDelay(id: String): User {
        record()
        return User(id, "no-delay")
    }

    @MockResponse(tag = DUPLICATED)
    fun duplicatedA(id: String): User = User(id, "a")

    @MockResponse(tag = DUPLICATED)
    fun duplicatedB(id: String): User = User(id, "b")
}

//任何請求真的送到網路層即失敗，用來確認 mock 不會發出網路請求
object RealNetworkInterceptor : Interceptor {
    const val MESSAGE = "REAL_NETWORK"
    val requests = AtomicInteger()

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        requests.incrementAndGet()
        throw IOException(MESSAGE)
    }
}

//mock 不會經過 converter，被呼叫即代表走了真實網路流程
object ThrowingConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *> = Converter<ResponseBody, Any> {
        throw AssertionError("converter should not be called for mock responses")
    }
}

fun testRetrofitBuilder(): Retrofit.Builder = Retrofit.Builder()
    .baseUrl(BASE_URL)
    .client(OkHttpClient.Builder().addInterceptor(RealNetworkInterceptor).build())
    .addConverterFactory(ThrowingConverterFactory)

fun Retrofit.Builder.createTestApi(): TestApi = build().create(TestApi::class.java)
