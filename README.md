# RetrofitMocker

以 annotation 為 Retrofit API 套用 mock 資料的輕量工具。

在 API 方法加上一行 `@MockRequest`，呼叫時就會改由你撰寫的 mocker 產生回應，不會發出網路請求；移除 annotation 即恢復真實請求。呼叫端與 API interface 的其他部分都不需要修改。

```kotlin
interface UserApi {
    @MockRequest(UserApiMocker::class, UserApiMocker.GET_USER_SUCCESS)
    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: String): User
}

class UserApiMocker {
    companion object {
        const val GET_USER_SUCCESS = "GET_USER_SUCCESS"
    }

    @MockResponse(tag = GET_USER_SUCCESS)
    fun getUser(id: String): User = User(id = id, name = "Mock User")
}
```

## 特色

- **不侵入**：沒有 `@MockRequest` 的 API 完全走原本流程。
- **支援任意回傳型別**：`suspend`、`Call<T>`、RxJava、`Flow` 或自訂 `CallAdapter`，mock 結果會交給原本的 `CallAdapter` 轉換。
- **依參數產生回應**：mocker 會收到本次呼叫的實際參數，可模擬分頁、依 ID 回傳等情境。
- **模擬錯誤**：回傳 `Response.error(...)` 模擬 HTTP 錯誤，拋出例外模擬網路失敗。
- **模擬延遲**：可針對單一 mock 或全域設定延遲，延遲期間可被取消。
- **行為貼近 OkHttp**：`enqueue` 於背景執行緒執行、`execute` 於呼叫端同步執行，回應帶有真實的 request。
- **設定錯誤立即報錯**：tag 找不到、重複或 factory 註冊順序錯誤時直接拋出錯誤，不會靜默改打真實 API。
- **內建 R8 規則**：開啟 R8 時會自動保留 mocker。

## 需求

- minSdk 21+
- Retrofit 2.11.0（以 `api` 依賴傳遞）

## 安裝

> 發佈設定完成後補上實際座標。

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// build.gradle.kts
dependencies {
    implementation("com.github.<USER>:<REPO>:<VERSION>")
}
```

## 使用方式

### 1. 註冊 `MockCallAdapterFactory`

```kotlin
val retrofit = Retrofit.Builder()
    .baseUrl("https://example.com/")
    .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
    .addMockCallAdapterFactory()  // 無論呼叫順序，都會被插入為第一個 CallAdapter.Factory
    .addConverterFactory(GsonConverterFactory.create())
    .build()
```

`MockCallAdapterFactory` **必須是第一個** `CallAdapter.Factory`，否則排在前面的 factory 會先處理 API，mock 將不會生效。建議使用 `addMockCallAdapterFactory()`；若使用 `addCallAdapterFactory(MockCallAdapterFactory())`，請確保它最先加入，順序錯誤時會在呼叫 API 時拋出錯誤。

### 2. 撰寫 mocker

```kotlin
class UserApiMocker {
    companion object {
        const val GET_USER_SUCCESS = "GET_USER_SUCCESS"
        const val GET_FOLLOWERS_SUCCESS = "GET_FOLLOWERS_SUCCESS"
    }

    @MockResponse(tag = GET_USER_SUCCESS)
    fun getUser(id: String): User = User(id = id, name = "Mock User")

    // 依參數回傳不同結果
    @MockResponse(tag = GET_FOLLOWERS_SUCCESS)
    fun getFollowers(id: String, cursor: String?): FollowerPage =
        if (cursor == null) FollowerPage(cursor = "1", users = firstPage)
        else FollowerPage(cursor = null, users = lastPage)
}
```

### 3. 在 API 方法加上 `@MockRequest`

```kotlin
interface UserApi {
    @MockRequest(UserApiMocker::class, UserApiMocker.GET_USER_SUCCESS)
    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: String): User

    @MockRequest(UserApiMocker::class, UserApiMocker.GET_FOLLOWERS_SUCCESS)
    @GET("users/{id}/followers")
    suspend fun getFollowers(@Path("id") id: String, @Query("cursor") cursor: String?): FollowerPage
}
```

## Mocker 規則

| 項目 | 規則 |
|---|---|
| 配對 | 以 `@MockRequest.mockerClass` + `tag` 找到 mocker 中 `@MockResponse.tag` 相同的方法，同一個 mocker 中 tag 不可重複 |
| 參數 | 與 API 方法的參數順序、數量、型別一致（`suspend` 方法不含 `Continuation`），mocker 方法本身不需要是 `suspend` |
| 回傳值 | 回傳 **body 型別**（例如 API 宣告 `suspend fun x(): User` 或 `Single<User>`，mocker 回傳 `User`），或回傳 `retrofit2.Response<body 型別>` 自行指定 HTTP code |
| 例外 | mocker 拋出的例外等同網路請求失敗 |
| 建構子 | mocker 需有 public 無參建構子（不可使用 Kotlin `object`），每個 API 方法會各自建立一個 mocker 實例 |

同一個 mocker 方法可以同時服務不同回傳型別的 API：

```kotlin
@MockRequest(UserApiMocker::class, UserApiMocker.GET_USER_SUCCESS)
@GET("users/{id}")
fun getUserSingle(@Path("id") id: String): Single<User>

@MockRequest(UserApiMocker::class, UserApiMocker.GET_USER_SUCCESS)
@GET("users/{id}")
fun getUserCall(@Path("id") id: String): Call<User>
```

## 模擬錯誤

**HTTP 錯誤**：回傳 `Response.error(...)`，會依各 `CallAdapter` 原本的錯誤流程處理（例如 `suspend` 拋出 `HttpException`）。

```kotlin
// OkHttp 4+ 寫法；OkHttp 3 請改用 ResponseBody.create(MediaType.get("application/json"), "...")
@MockResponse(tag = GET_USER_NOT_FOUND)
fun getUser(id: String): Response<User> = Response.error(
    404,
    """{"message":"user $id not found"}""".toResponseBody("application/json".toMediaType()),
)
```

**網路失敗**：直接拋出例外，`suspend` 會拋出該例外，`enqueue` 會回呼 `onFailure`。

```kotlin
@MockResponse(tag = GET_USER_NETWORK_ERROR)
fun getUser(id: String): User = throw IOException("network unavailable")
```

## 模擬延遲

```kotlin
// 單一 mock
@MockResponse(tag = GET_USER_SLOW, delayMillis = 2000)
fun getUser(id: String): User = ...

// 全域預設：未指定 delayMillis 的 mock 皆使用此延遲
Retrofit.Builder()
    .addMockCallAdapterFactory(MockCallAdapterFactory(defaultDelayMillis = 300))
```

- `@MockResponse.delayMillis` 未指定（預設 `-1`）時使用 `defaultDelayMillis`，指定為 `0` 則不延遲。
- 延遲期間呼叫 `Call.cancel()`（包含 coroutine 取消、RxJava dispose）會立即中斷並回報 `IOException("Canceled")`，mocker 不會被呼叫。

## 執行緒

與 OkHttp 相同：

- `enqueue`（`suspend`、RxJava async、`Call.enqueue`）：mocker 於背景執行緒執行並在該執行緒回呼，不會阻塞 main thread。
- `execute`：於呼叫端執行緒同步執行。

預設使用 cached thread pool，可自行指定：

```kotlin
MockCallAdapterFactory(executor = myExecutor)
```

## 只在 Debug 啟用

本工具不內建開關，請自行決定何時註冊 factory，避免 mock 進入正式版：

```kotlin
val builder = Retrofit.Builder().baseUrl(BASE_URL)
if (BuildConfig.DEBUG) {
    builder.addMockCallAdapterFactory()
}
```

未註冊 factory 時，`@MockRequest` 不會有任何作用，API 皆走真實請求。

## 注意事項

- **會繞過 OkHttp interceptor**：mock 不會經過 OkHttp，logging、網路檢視工具（例如 Chucker、Pluto）看不到 mock 的請求，auth header 等 interceptor 邏輯也不會執行。
- **會繞過 converter**：mocker 回傳的物件不經過 Gson / Moshi 等反序列化，請留意 mock 資料與真實 JSON 解析結果可能不同（例如 Kotlin non-null 欄位實際可能被 Gson 填入 null）。
- **參數於執行期檢查**：mocker 參數與 API 方法不一致時，會在呼叫時拋出例外，無法於編譯期檢查。
- **回應內容**：mock 的 `Response.raw()` 帶有本次呼叫的真實 request，HTTP code / message / headers 以 mocker 回傳為準（預設 `200 OK`）。

## R8 / ProGuard

已內建 consumer rules，開啟 R8 時會自動保留 annotation、mocker 類別的無參建構子與標註 `@MockResponse` 的方法（允許混淆名稱），不需額外設定。

## 範例

`app` module 為範例 app，示範：

- `suspend`、`Call<T>`、RxJava3 `Single<T>`、`Flow<T>`（`FlowCallAdapterFactory` 為最小範例實作）
- 依參數分頁、HTTP 404、延遲與取消
- tag 找不到、tag 重複、factory 順序錯誤時的錯誤訊息

執行 app 後結果會顯示在畫面上，並輸出至 Logcat（tag：`RetrofitMockerSample`）。
