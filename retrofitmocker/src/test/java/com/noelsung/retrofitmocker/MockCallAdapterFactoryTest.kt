package com.noelsung.retrofitmocker

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import java.io.IOException

//註冊方式與設定錯誤的檢查
class MockCallAdapterFactoryTest {

    @Before
    fun setUp() {
        TestMocker.reset()
        RealNetworkInterceptor.requests.set(0)
    }

    @Test
    fun `api without MockRequest uses real network`() = runBlocking {
        val api = testRetrofitBuilder().addCallAdapterFactory(MockCallAdapterFactory()).createTestApi()

        try {
            api.realUser("1")
            fail("expected real network request")
        } catch (e: IOException) {
            assertEquals(RealNetworkInterceptor.MESSAGE, e.message)
        }
        assertEquals(1, RealNetworkInterceptor.requests.get())
    }

    @Test
    fun `missing tag throws instead of falling back to real network`() = runBlocking {
        val api = testRetrofitBuilder().addCallAdapterFactory(MockCallAdapterFactory()).createTestApi()

        val cause = expectCallAdapterCreationFailure { api.missingTag("1") }
        assertTrue(cause.message!!.contains("@MockResponse(tag = \"MISSING_TAG\")"))
        assertTrue(cause.message!!.contains(TestMocker::class.java.name))
        assertEquals(0, RealNetworkInterceptor.requests.get())
    }

    @Test
    fun `duplicated tag throws and lists matched methods`() = runBlocking {
        val api = testRetrofitBuilder().addCallAdapterFactory(MockCallAdapterFactory()).createTestApi()

        val cause = expectCallAdapterCreationFailure { api.duplicatedTag("1") }
        assertTrue(cause.message!!.startsWith("Multiple methods"))
        assertTrue(cause.message!!.contains("duplicatedA"))
        assertTrue(cause.message!!.contains("duplicatedB"))
    }

    @Test
    fun `factory not registered first throws`() = runBlocking {
        val api = testRetrofitBuilder()
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .addCallAdapterFactory(MockCallAdapterFactory())
            .createTestApi()

        val cause = expectCallAdapterCreationFailure { api.user("1", null) }
        assertTrue(cause.message!!.startsWith("MockCallAdapterFactory must be the first CallAdapter.Factory"))
        assertTrue(cause.message!!.contains(RxJava3CallAdapterFactory::class.java.name))
    }

    @Test
    fun `addMockCallAdapterFactory inserts factory first regardless of call order`() {
        val factory = MockCallAdapterFactory()
        val builder = testRetrofitBuilder()
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .addMockCallAdapterFactory(factory)

        assertSame(factory, builder.callAdapterFactories().first())
        //RxJava3CallAdapterFactory 先加入，Single 仍會套用 mock
        assertEquals(User("1", "name-1-default"), builder.createTestApi().userSingle("1", null).blockingGet())
        assertEquals(1, TestMocker.invocations.get())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative defaultDelayMillis is rejected`() {
        MockCallAdapterFactory(defaultDelayMillis = -1)
    }

    //Retrofit 會將 CallAdapter 建立失敗包成 IllegalArgumentException，回傳實際原因
    private suspend fun expectCallAdapterCreationFailure(block: suspend () -> Any?): Throwable {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.startsWith("Unable to create call adapter"))
            val cause = e.cause!!
            assertTrue(cause is IllegalStateException)
            return cause
        }
        throw AssertionError("expected call adapter creation failure")
    }
}
