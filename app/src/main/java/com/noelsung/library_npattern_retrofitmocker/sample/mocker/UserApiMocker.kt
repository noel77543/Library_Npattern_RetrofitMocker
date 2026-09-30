package com.noelsung.library_npattern_retrofitmocker.sample.mocker

import android.util.Log
import com.noelsung.library_npattern_retrofitmocker.sample.model.FollowListResponse
import com.noelsung.library_npattern_retrofitmocker.sample.model.FollowListResponseItem
import com.noelsung.library_npattern_retrofitmocker.sample.model.UserResponseBody
import com.noelsung.retrofitmocker.annotations.MockResponse
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.Response

class UserApiMocker {
    companion object {
        const val GET_USER_PROFILE_SUCCESS = "GET_USER_PROFILE_SUCCESS"
        const val GET_USER_PROFILE_NOT_FOUND = "GET_USER_PROFILE_NOT_FOUND"
        const val GET_USER_PROFILE_SLOW = "GET_USER_PROFILE_SLOW"
        const val FOLLOW_USER_SUCCESS = "FOLLOW_USER_SUCCESS"
        const val UNFOLLOW_USER_SUCCESS = "UNFOLLOW_USER_SUCCESS"
        const val GET_FOLLOWERS_LIST_SUCCESS = "GET_FOLLOWERS_LIST_SUCCESS"
    }

    @MockResponse(tag = GET_USER_PROFILE_SUCCESS)
    fun apiGetUser(
        hidolID: String,
    ): UserResponseBody {
        return UserResponseBody(
            about = "HAHAHAHAHAHAHAHA",
            avatar = null,
            createdTime = 999,
            name = "QQQ",
            hidolID = hidolID,
            updatedTime = 999,
            role = 1,
            isFollowing = false,
            isFollowedBy = false,
            followerCount = 0,
            followingCount = 0,
        )
    }

    //delayMillis 模擬網路延遲；enqueue (suspend / RxJava / Call.enqueue) 時 mocker 於背景執行緒執行
    @MockResponse(tag = GET_USER_PROFILE_SLOW, delayMillis = 2000)
    fun apiGetUserSlow(
        hidolID: String,
    ): UserResponseBody {
        Log.d("UserApiMocker", "apiGetUserSlow invoked: hidolID=$hidolID")
        return apiGetUser(hidolID).copy(about = "mocked on thread ${Thread.currentThread().name}")
    }

    //回傳 Response<T> 可自行指定 http code，會依照各 CallAdapter 的錯誤處理流程拋出 (例如 HttpException)
    @MockResponse(tag = GET_USER_PROFILE_NOT_FOUND)
    fun apiGetUserNotFound(
        hidolID: String,
    ): Response<UserResponseBody> {
        return Response.error(
            404,
            ResponseBody.create(
                MediaType.get("application/json"),
                """{"message":"user $hidolID not found"}""",
            ),
        )
    }

    @MockResponse(tag = FOLLOW_USER_SUCCESS)
    fun apiFollowUser(
        hidolID: String,
    ) {
    }

    @MockResponse(tag = UNFOLLOW_USER_SUCCESS)
    fun apiUnfollowUser(
        hidolID: String,
    ) {
    }

    @MockResponse(tag = GET_FOLLOWERS_LIST_SUCCESS)
    fun apiGetFollowersList(
        hidolID: String,
        cursor: String?,
        size: Int?,
    ): FollowListResponse = if (cursor == null) {
        FollowListResponse(
            cursor = "1",
            hasNext = true,
            followers = listOf(
                FollowListResponseItem(hidolID = "1", name = "Sylvie", avatar = null, isFollowing = true),
                FollowListResponseItem(hidolID = "2", name = "Kevin", avatar = null, isFollowing = false),
            ),
        )
    } else {
        FollowListResponse(
            cursor = null,
            hasNext = false,
            followers = listOf(
                FollowListResponseItem(hidolID = "3", name = "Amy", avatar = null, isFollowing = true),
            ),
        )
    }
}
