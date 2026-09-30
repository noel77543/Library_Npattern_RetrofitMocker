package com.noelsung.library_npattern_retrofitmocker.sample.api

import com.noelsung.library_npattern_retrofitmocker.sample.mocker.MisconfiguredMocker
import com.noelsung.library_npattern_retrofitmocker.sample.mocker.UserApiMocker
import com.noelsung.library_npattern_retrofitmocker.sample.model.FollowListResponse
import com.noelsung.library_npattern_retrofitmocker.sample.model.UserResponseBody
import com.noelsung.retrofitmocker.annotations.MockRequest
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.flow.Flow
import retrofit2.Call
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface UserApi {

    // 取得使用者資訊
    @MockRequest(UserApiMocker::class, UserApiMocker.GET_USER_PROFILE_SUCCESS)
    @GET("v3/users/{hidolID}")
    suspend fun apiGetUser(
        @Path("hidolID")
        hidolID: String,
    ): UserResponseBody

    // 同一個 mocker method 可同時服務不同回傳型別的 api：Call<T>
    @MockRequest(UserApiMocker::class, UserApiMocker.GET_USER_PROFILE_SUCCESS)
    @GET("v3/users/{hidolID}")
    fun apiGetUserCall(
        @Path("hidolID")
        hidolID: String,
    ): Call<UserResponseBody>

    // RxJava3 Single<T>，由 RxJava3CallAdapterFactory 轉換
    @MockRequest(UserApiMocker::class, UserApiMocker.GET_USER_PROFILE_SUCCESS)
    @GET("v3/users/{hidolID}")
    fun apiGetUserSingle(
        @Path("hidolID")
        hidolID: String,
    ): Single<UserResponseBody>

    // Flow<T>，由 FlowCallAdapterFactory 轉換
    @MockRequest(UserApiMocker::class, UserApiMocker.GET_USER_PROFILE_SUCCESS)
    @GET("v3/users/{hidolID}")
    fun apiGetUserFlow(
        @Path("hidolID")
        hidolID: String,
    ): Flow<UserResponseBody>

    // mocker 回傳 Response.error(404, ...) 模擬 http 錯誤
    @MockRequest(UserApiMocker::class, UserApiMocker.GET_USER_PROFILE_NOT_FOUND)
    @GET("v3/users/{hidolID}")
    suspend fun apiGetUserNotFound(
        @Path("hidolID")
        hidolID: String,
    ): UserResponseBody

    // mocker 以 @MockResponse(delayMillis = 2000) 模擬網路延遲
    @MockRequest(UserApiMocker::class, UserApiMocker.GET_USER_PROFILE_SLOW)
    @GET("v3/users/{hidolID}")
    suspend fun apiGetUserSlow(
        @Path("hidolID")
        hidolID: String,
    ): UserResponseBody

    // 錯誤示範：mocker 中找不到對應 tag，呼叫時會拋出錯誤而非改打真實 api
    @MockRequest(MisconfiguredMocker::class, MisconfiguredMocker.MISSING_TAG)
    @GET("v3/users/{hidolID}")
    suspend fun apiGetUserMissingTag(
        @Path("hidolID")
        hidolID: String,
    ): UserResponseBody

    // 錯誤示範：mocker 中有多個 method 使用相同 tag，呼叫時會拋出錯誤
    @MockRequest(MisconfiguredMocker::class, MisconfiguredMocker.DUPLICATED_TAG)
    @GET("v3/users/{hidolID}")
    suspend fun apiGetUserDuplicatedTag(
        @Path("hidolID")
        hidolID: String,
    ): UserResponseBody

    @MockRequest(UserApiMocker::class, UserApiMocker.FOLLOW_USER_SUCCESS)
    @POST("v1/users/{hidolID}/follow")
    suspend fun apiFollowUser(
        @Path("hidolID") hidolID: String,
    ): Unit

    @MockRequest(UserApiMocker::class, UserApiMocker.UNFOLLOW_USER_SUCCESS)
    @DELETE("v1/users/{hidolID}/follow")
    suspend fun apiUnfollowUser(
        @Path("hidolID") hidolID: String,
    ): Unit

    @MockRequest(UserApiMocker::class, UserApiMocker.GET_FOLLOWERS_LIST_SUCCESS)
    @GET("v1/users/{hidolID}/followers")
    suspend fun apiGetFollowersList(
        @Path("hidolID") hidolID: String,
        @Query("cursor") cursor: String?,
        @Query("size") size: Int?,
    ): FollowListResponse
}
