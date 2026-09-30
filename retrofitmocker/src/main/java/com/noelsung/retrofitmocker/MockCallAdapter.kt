package com.noelsung.retrofitmocker

import okhttp3.Request
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Invocation
import retrofit2.Response
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.util.concurrent.Executor


internal class MockCallAdapter<T>(
    private val mockMethod: Method,
    private val mockerInstance: Any,
    private val delegate: CallAdapter<T, *>,
    private val delayMillis: Long,
    private val executor: Executor,
) : CallAdapter<T, Any> {

    override fun responseType(): Type = delegate.responseType()

    override fun adapt(call: Call<T>): Any {
        //Invocation 是 Retrofit 2.4.0+ 引入的類，用於捕獲對 Retrofit 服務介面方法的呼叫以及方法的參數。
        //它被當作每個 OkHttp 請求的 tag，你可以在 OkHttp 攔截器中透過 request.tag(Invocation.class) 來取得這些資訊。
        // 這個特性非常適合用於記錄、監控或調試 API 呼叫。
        val invocation = call.request().tag(Invocation::class.java)
            ?: return delegate.adapt(call)!!

        val mockCall = MockCall(call, delayMillis, executor) { request ->
            createMockResponse(invocation, request)
        }
        //將 mockCall 交給下一個 CallAdapter 轉換成 api 宣告的回傳型別 (Call、suspend、Observable、Flow...)
        return delegate.adapt(mockCall)!!
    }

    private fun createMockResponse(invocation: Invocation, request: Request): Response<T> {
        val result = try {
            mockMethod.invoke(
                mockerInstance,
                //使用*來展開列表
                *invocation.arguments().toTypedArray()
            )
        } catch (e: InvocationTargetException) {
            //拆出 mocker 內實際拋出的例外
            throw e.targetException
        }

        //mocker 可回傳 Response<R> 自行決定 http code (例如 Response.error(404, ...))，否則視為成功的 body
        @Suppress("UNCHECKED_CAST")
        val response = if (result is Response<*>) {
            result as Response<T>
        } else {
            Response.success(result as T)
        }
        return response.withRequest(request)
    }

    //Retrofit 建立的 Response 其 raw().request() 固定為 http://localhost/，
    //替換為真實 request 並保留 mocker 指定的 code / message / headers
    private fun Response<T>.withRequest(request: Request): Response<T> {
        val now = System.currentTimeMillis()
        val rawResponse = raw().newBuilder()
            .request(request)
            .sentRequestAtMillis(now)
            .receivedResponseAtMillis(now)
            .build()
        return if (isSuccessful) {
            Response.success(body(), rawResponse)
        } else {
            Response.error(errorBody()!!, rawResponse)
        }
    }
}
