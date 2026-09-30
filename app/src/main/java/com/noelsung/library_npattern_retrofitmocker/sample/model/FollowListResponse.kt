package com.noelsung.library_npattern_retrofitmocker.sample.model

data class FollowListResponse(
    val cursor: String?,
    val hasNext: Boolean,
    val followers: List<FollowListResponseItem>,
)

data class FollowListResponseItem(
    val hidolID: String,
    val name: String,
    val avatar: String?,
    val isFollowing: Boolean,
)
