package com.noelsung.library_npattern_retrofitmocker.sample.mocker

import com.noelsung.library_npattern_retrofitmocker.sample.model.UserResponseBody
import com.noelsung.retrofitmocker.annotations.MockResponse

//故意錯誤設定的 mocker，用來示範 tag 找不到或重複時 MockCallAdapterFactory 會直接拋出錯誤
class MisconfiguredMocker {
    companion object {
        //沒有任何 method 使用此 tag
        const val MISSING_TAG = "MISSING_TAG"
        //有兩個 method 使用此 tag
        const val DUPLICATED_TAG = "DUPLICATED_TAG"
    }

    @MockResponse(tag = DUPLICATED_TAG)
    fun apiGetUserA(hidolID: String): UserResponseBody = throw UnsupportedOperationException()

    @MockResponse(tag = DUPLICATED_TAG)
    fun apiGetUserB(hidolID: String): UserResponseBody = throw UnsupportedOperationException()
}
