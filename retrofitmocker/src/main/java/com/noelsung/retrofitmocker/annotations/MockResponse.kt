package com.noelsung.retrofitmocker.annotations


@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MockResponse(
    //與 MockRequest.tag 相呼應
    val tag: String,
    //模擬網路延遲 (毫秒)，小於 0 時使用 MockCallAdapterFactory 的 defaultDelayMillis
    val delayMillis: Long = -1,
)
