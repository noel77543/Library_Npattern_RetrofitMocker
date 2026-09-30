package com.noelsung.retrofitmocker

import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


//取代真實網路請求的 Call，會被交給下一個 CallAdapter，因此需符合 Call 的完整行為 (clone / cancel / 只能執行一次)
internal class MockCall<T>(
    //原始的 Call，僅用於提供 request 與 timeout，不會被執行
    private val originalCall: Call<T>,
    //模擬網路延遲 (毫秒)
    private val delayMillis: Long,
    //enqueue 時執行 mocker 的執行緒池
    private val executor: Executor,
    //傳入此 Call 的 request，讓 mock 的 Response 能對應到真實請求
    private val mockResponse: (Request) -> Response<T>,
) : Call<T> {

    private val executed = AtomicBoolean(false)

    //cancel 時釋放，用來中斷延遲等待
    private val cancelSignal = CountDownLatch(1)

    @Volatile
    private var canceled = false

    //與 OkHttp 相同，execute 於呼叫端執行緒同步執行
    override fun execute(): Response<T> {
        check(executed.compareAndSet(false, true)) { "Already executed." }
        awaitDelay()
        return mockResponse(request())
    }

    //與 OkHttp 相同，enqueue 於背景執行緒執行 mocker 並在該執行緒回呼，避免阻塞呼叫端 (例如 main thread)
    override fun enqueue(callback: Callback<T>) {
        check(executed.compareAndSet(false, true)) { "Already executed." }
        executor.execute {
            val response = try {
                awaitDelay()
                mockResponse(request())
            } catch (t: Throwable) {
                callback.onFailure(this, t)
                return@execute
            }
            callback.onResponse(this, response)
        }
    }

    //模擬網路延遲，期間被 cancel 時立即中斷
    private fun awaitDelay() {
        if (delayMillis > 0) {
            try {
                cancelSignal.await(delayMillis, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Interrupted")
            }
        }
        if (canceled) throw IOException("Canceled")
    }

    override fun isExecuted(): Boolean = executed.get()

    override fun cancel() {
        canceled = true
        cancelSignal.countDown()
    }

    override fun isCanceled(): Boolean = canceled

    override fun request(): Request = originalCall.request()

    override fun timeout(): Timeout = originalCall.timeout()

    //RxJava 等 adapter 每次訂閱都會 clone，需回傳全新且未執行的 Call
    override fun clone(): Call<T> = MockCall(originalCall.clone(), delayMillis, executor, mockResponse)
}
