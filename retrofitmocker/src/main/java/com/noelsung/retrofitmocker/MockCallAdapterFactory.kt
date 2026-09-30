package com.noelsung.retrofitmocker

import com.noelsung.retrofitmocker.annotations.MockRequest
import com.noelsung.retrofitmocker.annotations.MockResponse
import retrofit2.CallAdapter
import retrofit2.Retrofit
import java.lang.reflect.Type
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class MockCallAdapterFactory @JvmOverloads constructor(
    //未於 @MockResponse 指定 delayMillis 時使用的模擬網路延遲 (毫秒)
    private val defaultDelayMillis: Long = 0,
    //enqueue 時執行 mocker 的執行緒池
    private val executor: Executor = defaultExecutor,
) : CallAdapter.Factory() {

    init {
        require(defaultDelayMillis >= 0) { "defaultDelayMillis < 0: $defaultDelayMillis" }
    }

    override fun get(
        returnType: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit,
    ): CallAdapter<*, *> {

        //需為第一個 CallAdapter.Factory，否則排在前面的 factory 會先處理 api 的回傳型別，導致 mock 被靜默忽略
        val factories = retrofit.callAdapterFactories()
        val index = factories.indexOf(this)
        check(index <= 0) {
            "MockCallAdapterFactory must be the first CallAdapter.Factory, but found after " +
                "${factories.take(index).joinToString { it.javaClass.name }}. " +
                "Use Retrofit.Builder.addMockCallAdapterFactory() or add it before other factories."
        }

        //默認的CallAdapter，用於處理非 Mock 的情況
        val delegate = retrofit.nextCallAdapter(this, returnType, annotations)
        val mockRequest = annotations.find { it is MockRequest } as? MockRequest
            ?: return delegate

        val mocker = mockRequest.mockerClass

        //反射mocker中所有method
        val functions = mocker.java.declaredMethods
        //找到有MockResponse 的annotation且 tag 相符的
        val matchedMethods = functions.filter {
            it.getAnnotation(MockResponse::class.java)?.tag == mockRequest.tag
        }

        //已標註 MockRequest 代表明確要使用 mock，找不到或重複時直接拋出錯誤，避免靜默改打真實 api
        check(matchedMethods.isNotEmpty()) {
            "No method annotated with @MockResponse(tag = \"${mockRequest.tag}\") " +
                "found in ${mocker.java.name}"
        }
        check(matchedMethods.size == 1) {
            "Multiple methods annotated with @MockResponse(tag = \"${mockRequest.tag}\") " +
                "found in ${mocker.java.name}: ${matchedMethods.joinToString { it.name }}"
        }
        val method = matchedMethods.single()

        val instance = mocker.java.getDeclaredConstructor().newInstance()
        val delayMillis = method.getAnnotation(MockResponse::class.java)!!.delayMillis
            .takeIf { it >= 0 } ?: defaultDelayMillis
        return MockCallAdapter(method, instance, delegate, delayMillis, executor)
    }

    private companion object {
        //與 OkHttp Dispatcher 相同使用 cached thread pool，daemon thread 避免阻止 JVM 結束
        val defaultExecutor: Executor by lazy {
            Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "RetrofitMocker").apply { isDaemon = true }
            }
        }
    }
}