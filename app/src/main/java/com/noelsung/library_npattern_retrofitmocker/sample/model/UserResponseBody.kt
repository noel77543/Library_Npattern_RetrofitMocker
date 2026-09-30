package com.noelsung.library_npattern_retrofitmocker.sample.model

data class UserResponseBody(
    val about: String?,
    val avatar: String?,
    val createdTime: Long,
    val name: String,
    val hidolID: String,
    val updatedTime: Long,
    val role: Int,
    val isFollowing: Boolean,
    val isFollowedBy: Boolean,
    val followerCount: Int,
    val followingCount: Int,
)
