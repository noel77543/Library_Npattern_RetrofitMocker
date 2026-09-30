package com.noelsung.retrofitmocker.annotations

import kotlin.reflect.KClass


//在任何 retrofit api interface 的func上添加 此 annotation 即可套用mock邏輯
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MockRequest(
    val mockerClass: KClass<*>,
    //與 MockResponse.tag 相呼應
    val tag: String,
)

