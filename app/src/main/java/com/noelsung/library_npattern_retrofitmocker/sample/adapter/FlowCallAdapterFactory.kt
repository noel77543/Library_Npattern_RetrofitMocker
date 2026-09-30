package com.noelsung.library_npattern_retrofitmocker.sample.adapter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.awaitResponse
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

//Retrofit 沒有官方的 Flow adapter，這裡提供最小實作，用來示範 MockCallAdapterFactory 可與任意 CallAdapter 搭配
class FlowCallAdapterFactory : CallAdapter.Factory() {
    override fun get(
        returnType: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit,
    ): CallAdapter<*, *>? {
        if (getRawType(returnType) != Flow::class.java) return null
        check(returnType is ParameterizedType) { "Flow return type must be parameterized as Flow<Foo>" }
        return FlowCallAdapter<Any>(getParameterUpperBound(0, returnType))
    }
}

private class FlowCallAdapter<T : Any>(
    private val responseType: Type,
) : CallAdapter<T, Flow<T>> {
    override fun responseType(): Type = responseType

    //每次 collect 都 clone 一個新的 Call 執行
    override fun adapt(call: Call<T>): Flow<T> = flow {
        val response = call.clone().awaitResponse()
        if (!response.isSuccessful) throw HttpException(response)
        @Suppress("UNCHECKED_CAST")
        emit(response.body() as T)
    }
}
