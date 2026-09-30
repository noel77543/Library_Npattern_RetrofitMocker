package com.noelsung.retrofitmocker

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

//mocker 回傳值如何轉換成各種 api 回傳型別
class MockResponseTest {

    private val api = testRetrofitBuilder()
        .addMockCallAdapterFactory()
        .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
        .createTestApi()

    @Before
    fun setUp() {
        TestMocker.reset()
        RealNetworkInterceptor.requests.set(0)
    }

    @After
    fun tearDown() {
        //mock 不應經過 OkHttp 網路層
        assertEquals(0, RealNetworkInterceptor.requests.get())
    }

    @Test
    fun `suspend api returns mocker body with invocation arguments`() = runBlocking {
        assertEquals(User("1", "name-1-zh"), api.user("1", "zh"))
        assertEquals(User("2", "name-2-default"), api.user("2", null))
        assertEquals(2, TestMocker.invocations.get())
    }

    @Test
    fun `Call api execute returns successful response`() {
        val response = api.userCall("1", "en").execute()

        assertTrue(response.isSuccessful)
        assertEquals(200, response.code())
        assertEquals(User("1", "name-1-en"), response.body())
    }

    @Test
    fun `suspend Unit api is supported`() = runBlocking {
        api.follow("1")
        assertEquals(1, TestMocker.invocations.get())
    }

    @Test
    fun `RxJava Single api is converted by delegate adapter`() {
        assertEquals(User("1", "name-1-default"), api.userSingle("1", null).blockingGet())
    }

    @Test
    fun `each RxJava subscription clones call and invokes mocker again`() {
        val single = api.userSingle("1", null)
        single.blockingGet()
        single.blockingGet()
        assertEquals(2, TestMocker.invocations.get())
    }

    @Test
    fun `mocker Response error is thrown as HttpException for suspend api`() = runBlocking {
        try {
            api.notFound("404")
            fail("expected HttpException")
        } catch (e: HttpException) {
            assertEquals(404, e.code())
            assertEquals("""{"message":"404 not found"}""", e.response()!!.errorBody()!!.string())
        }
    }

    @Test
    fun `raw response carries real request for success and error`() {
        val success = api.userCall("1", "zh").execute()
        assertEquals("${BASE_URL}users/1?lang=zh", success.raw().request().url().toString())
        assertEquals("GET", success.raw().request().method())

        val error = api.notFoundCall("404").execute()
        assertFalse(error.isSuccessful)
        assertEquals(404, error.code())
        assertEquals("${BASE_URL}users/404", error.raw().request().url().toString())
    }

    @Test
    fun `mocker exception is propagated as network failure for suspend api`() = runBlocking {
        try {
            api.networkError("1")
            fail("expected IOException")
        } catch (e: IOException) {
            assertEquals("mock network error: 1", e.message)
        }
    }

    @Test
    fun `mocker exception is thrown from execute`() {
        try {
            api.networkErrorCall("1").execute()
            fail("expected IOException")
        } catch (e: IOException) {
            assertEquals("mock network error: 1", e.message)
        }
    }

    @Test
    fun `mocker exception is delivered to onFailure for enqueue`() {
        val failure = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)

        api.networkErrorCall("1").enqueue(object : Callback<User> {
            override fun onResponse(call: Call<User>, response: Response<User>) = latch.countDown()
            override fun onFailure(call: Call<User>, t: Throwable) {
                failure.set(t)
                latch.countDown()
            }
        })

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertTrue(failure.get() is IOException)
        assertEquals("mock network error: 1", failure.get().message)
    }
}
