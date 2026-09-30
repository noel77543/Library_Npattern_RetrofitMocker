@file:JvmName("RetrofitMocker")

package com.noelsung.retrofitmocker

import retrofit2.Retrofit

//將 MockCallAdapterFactory 插入為第一個 CallAdapter.Factory，不受與其他 addCallAdapterFactory 的呼叫順序影響
fun Retrofit.Builder.addMockCallAdapterFactory(
    factory: MockCallAdapterFactory = MockCallAdapterFactory(),
): Retrofit.Builder = apply {
    //Retrofit.Builder.callAdapterFactories() 回傳可修改的 list
    callAdapterFactories().add(0, factory)
}
