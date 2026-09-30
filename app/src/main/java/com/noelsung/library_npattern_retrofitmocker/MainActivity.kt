package com.noelsung.library_npattern_retrofitmocker

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.noelsung.library_npattern_retrofitmocker.sample.adapter.FlowCallAdapterFactory
import com.noelsung.library_npattern_retrofitmocker.sample.api.UserApi
import com.noelsung.retrofitmocker.MockCallAdapterFactory
import com.noelsung.retrofitmocker.addMockCallAdapterFactory
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.await
import retrofit2.awaitResponse
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private val userApi: UserApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://example.com/")
            //MockCallAdapterFactory 必須最先加入，mock 結果會再交給後面的 CallAdapter 轉換
            .addCallAdapterFactory(MockCallAdapterFactory())
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .addCallAdapterFactory(FlowCallAdapterFactory())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UserApi::class.java)
    }

    //使用 addMockCallAdapterFactory()，即使最後才加入也會被插入為第一個 CallAdapter.Factory
    private val extensionUserApi: UserApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://example.com/")
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .addCallAdapterFactory(FlowCallAdapterFactory())
            .addMockCallAdapterFactory()
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UserApi::class.java)
    }

    //錯誤示範：MockCallAdapterFactory 未最先加入，呼叫時會拋出錯誤而非靜默忽略 mock
    private val misorderedUserApi: UserApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://example.com/")
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .addCallAdapterFactory(MockCallAdapterFactory())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UserApi::class.java)
    }

    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (16 * resources.displayMetrics.density).toInt()
        logView = TextView(this).apply { setPadding(padding, padding, padding, padding) }
        setContentView(ScrollView(this).apply { addView(logView) })

        lifecycleScope.launch {
            // suspend
            sample("suspend apiGetUser(\"hidol_001\")") { userApi.apiGetUser("hidol_001") }
            sample("suspend apiFollowUser(\"hidol_001\")") { userApi.apiFollowUser("hidol_001") }
            sample("suspend apiUnfollowUser(\"hidol_001\")") { userApi.apiUnfollowUser("hidol_001") }

            val firstPage = sample("suspend apiGetFollowersList(cursor = null)") {
                userApi.apiGetFollowersList("hidol_001", cursor = null, size = 20)
            }
            sample("suspend apiGetFollowersList(cursor = \"${firstPage?.cursor}\")") {
                userApi.apiGetFollowersList("hidol_001", cursor = firstPage?.cursor, size = 20)
            }

            // Call<T>
            sample("Call apiGetUserCall(\"hidol_002\")") { userApi.apiGetUserCall("hidol_002").await() }

            // mock 的 Response 會帶有真實的 request
            sample("Call apiGetUserCall(\"hidol_002\").awaitResponse().raw()") {
                userApi.apiGetUserCall("hidol_002").awaitResponse().raw()
            }

            // RxJava3 Single<T>
            sample("Single apiGetUserSingle(\"hidol_003\")") { userApi.apiGetUserSingle("hidol_003").await() }

            // Flow<T>
            sample("Flow apiGetUserFlow(\"hidol_004\")") { userApi.apiGetUserFlow("hidol_004").first() }

            // mocker 回傳 Response.error(404, ...)
            sample("suspend apiGetUserNotFound(\"hidol_404\")") {
                try {
                    userApi.apiGetUserNotFound("hidol_404")
                } catch (e: HttpException) {
                    "HttpException code=${e.code()} body=${e.response()?.errorBody()?.string()}" +
                        "\n  raw=${e.response()?.raw()}"
                }
            }

            // 模擬延遲：mocker 於背景執行緒執行，main thread 不會被阻塞
            sample("suspend apiGetUserSlow(\"hidol_007\") [delayMillis = 2000]") {
                val start = SystemClock.elapsedRealtime()
                val user = userApi.apiGetUserSlow("hidol_007")
                "${user.about}, elapsed=${SystemClock.elapsedRealtime() - start}ms"
            }

            // 延遲期間取消：coroutine 取消會呼叫 Call.cancel()，mocker 不會被執行
            sample("withTimeout(500) { apiGetUserSlow(\"hidol_008\") }") {
                try {
                    withTimeout(500) { userApi.apiGetUserSlow("hidol_008") }
                } catch (e: TimeoutCancellationException) {
                    "canceled: ${e.message}"
                }
            }

            // addMockCallAdapterFactory()：最後才加入 mock factory，RxJava 的 api 仍會套用 mock
            sample("[addMockCallAdapterFactory] Single apiGetUserSingle(\"hidol_009\")") {
                extensionUserApi.apiGetUserSingle("hidol_009").await()
            }

            // 錯誤示範：MockCallAdapterFactory 排在 RxJava3CallAdapterFactory 之後
            sample("[misordered] suspend apiGetUser(\"hidol_010\")") {
                expectMisconfiguration { misorderedUserApi.apiGetUser("hidol_010") }
            }

            // 錯誤示範：tag 找不到 / tag 重複
            sample("suspend apiGetUserMissingTag(\"hidol_005\")") {
                expectMisconfiguration { userApi.apiGetUserMissingTag("hidol_005") }
            }
            sample("suspend apiGetUserDuplicatedTag(\"hidol_006\")") {
                expectMisconfiguration { userApi.apiGetUserDuplicatedTag("hidol_006") }
            }
        }
    }

    private suspend fun <T> sample(title: String, block: suspend () -> T): T? {
        return runCatching { block() }
            .onSuccess { log(title, it) }
            .onFailure { log("$title FAILED", it.stackTraceToString()) }
            .getOrNull()
    }

    //Retrofit 會將 CallAdapter 建立失敗包成 IllegalArgumentException，實際原因在 cause
    private suspend fun expectMisconfiguration(block: suspend () -> Any?): String {
        return try {
            "Unexpected success: ${block()}"
        } catch (e: IllegalArgumentException) {
            "${e.message}\n  caused by: ${e.cause}"
        }
    }

    private fun log(title: String, result: Any?) {
        Log.d(TAG, "▶ $title\n$result")
        logView.append("▶ $title\n$result\n\n")
    }

    companion object {
        private const val TAG = "RetrofitMockerSample"
    }
}
