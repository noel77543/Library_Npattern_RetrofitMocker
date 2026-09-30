package com.noelsung.retrofitmocker

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

//MockCall 的執行緒、延遲、取消與 Call 規範
class MockCallTest {

    private val mockExecutor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, MOCK_THREAD_NAME).apply { isDaemon = true }
    }

    private val api = testRetrofitBuilder()
        .addMockCallAdapterFactory(
            MockCallAdapterFactory(defaultDelayMillis = DEFAULT_DELAY_MILLIS, executor = mockExecutor)
        )
        .createTestApi()

    @Before
    fun setUp() {
        TestMocker.reset()
    }

    @After
    fun tearDown() {
        mockExecutor.shutdownNow()
    }

    @Test
    fun `enqueue runs mocker and callback on executor thread`() {
        val callbackThread = AtomicReference<String>()
        val latch = CountDownLatch(1)

        api.noDelayCall("1").enqueue(object : Callback<User> {
            override fun onResponse(call: Call<User>, response: Response<User>) {
                callbackThread.set(Thread.currentThread().name)
                latch.countDown()
            }

            override fun onFailure(call: Call<User>, t: Throwable) = latch.countDown()
        })

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(MOCK_THREAD_NAME, TestMocker.lastThreadName)
        assertEquals(MOCK_THREAD_NAME, callbackThread.get())
    }

    @Test
    fun `suspend api runs mocker on executor thread`() = runBlocking {
        api.slow("1")
        assertEquals(MOCK_THREAD_NAME, TestMocker.lastThreadName)
    }

    @Test
    fun `execute runs mocker on caller thread`() {
        api.noDelayCall("1").execute()
        assertEquals(Thread.currentThread().name, TestMocker.lastThreadName)
    }

    @Test
    fun `annotation delayMillis is applied`() {
        val elapsed = measureMillis { api.slowCall("1").execute() }
        assertTrue("elapsed=$elapsed", elapsed >= TestMocker.SLOW_DELAY_MILLIS)
    }

    @Test
    fun `factory defaultDelayMillis is applied when annotation does not specify delay`() {
        val elapsed = measureMillis { api.defaultDelayCall("1").execute() }
        assertTrue("elapsed=$elapsed", elapsed >= DEFAULT_DELAY_MILLIS)
    }

    @Test
    fun `annotation delayMillis 0 overrides factory default`() {
        val elapsed = measureMillis { api.noDelayCall("1").execute() }
        assertTrue("elapsed=$elapsed", elapsed < DEFAULT_DELAY_MILLIS)
    }

    @Test
    fun `cancel during delay interrupts execute and skips mocker`() {
        val call = api.cancelableCall("1")
        val future = Executors.newSingleThreadExecutor().submit<Response<User>> { call.execute() }

        Thread.sleep(50)
        call.cancel()
        try {
            //cancel 需立即中斷等待，而非等到延遲結束
            future.get(CANCEL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            fail("expected IOException")
        } catch (e: ExecutionException) {
            assertTrue(e.cause is IOException)
            assertEquals("Canceled", e.cause!!.message)
        }

        assertTrue(call.isCanceled)
        assertEquals(0, TestMocker.invocations.get())
    }

    @Test
    fun `cancel during delay delivers onFailure and skips mocker`() {
        val failure = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        val call = api.cancelableCall("1")

        call.enqueue(object : Callback<User> {
            override fun onResponse(call: Call<User>, response: Response<User>) = latch.countDown()
            override fun onFailure(call: Call<User>, t: Throwable) {
                failure.set(t)
                latch.countDown()
            }
        })
        Thread.sleep(50)
        call.cancel()

        //cancel 需立即中斷等待，而非等到延遲結束
        assertTrue(latch.await(CANCEL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        assertTrue(failure.get() is IOException)
        assertEquals("Canceled", failure.get().message)
        assertEquals(0, TestMocker.invocations.get())
    }

    @Test
    fun `coroutine cancellation cancels call and skips mocker`() = runBlocking {
        try {
            withTimeout(50) { api.slow("1") }
            fail("expected timeout")
        } catch (e: TimeoutCancellationException) {
            //expected
        }

        //等待超過原本的延遲時間，確認 mocker 未被呼叫
        Thread.sleep(TestMocker.SLOW_DELAY_MILLIS + 100)
        assertEquals(0, TestMocker.invocations.get())
    }

    @Test
    fun `call can only be executed once`() {
        val call = api.noDelayCall("1")
        assertFalse(call.isExecuted)

        call.execute()
        assertTrue(call.isExecuted)

        try {
            call.execute()
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("Already executed.", e.message)
        }
    }

    @Test
    fun `clone returns new unexecuted call`() {
        val call = api.noDelayCall("1")
        call.execute()

        val cloned = call.clone()
        assertNotSame(call, cloned)
        assertFalse(cloned.isExecuted)
        assertEquals(User("1", "no-delay"), cloned.execute().body())
        assertEquals(2, TestMocker.invocations.get())
    }

    private inline fun measureMillis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
    }

    private companion object {
        const val MOCK_THREAD_NAME = "test-mock-executor"
        //需明顯大於無延遲 mock 的執行時間，避免高負載下誤判
        const val DEFAULT_DELAY_MILLIS = 1000L

        //遠小於 TestMocker.CANCELABLE_DELAY_MILLIS，超過即代表 cancel 未中斷等待
        const val CANCEL_TIMEOUT_MILLIS = 2000L
    }
}
