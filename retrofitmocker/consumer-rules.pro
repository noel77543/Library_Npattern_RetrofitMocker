# RetrofitMocker consumer keep rules
# 這些規則會隨 AAR 一起發佈，自動套用到引用此 lib 的專案

# 在執行期透過反射讀取 @MockRequest / @MockResponse，需保留 annotation 本身與其屬性
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keep class com.noelsung.retrofitmocker.annotations.** { *; }

# mocker 只被 @MockRequest.mockerClass 參照且只透過反射呼叫，R8 會視為未使用而整個移除；
# 只要專案有使用 MockCallAdapterFactory，就保留有 @MockResponse 的 method 與其所屬類別的無參建構子
# (MockCallAdapterFactory 以 newInstance() 建立)。
# 若 release 以 BuildConfig.DEBUG 等條件未使用 MockCallAdapterFactory，R8 移除 factory 後 mocker 也會一併被移除。
# 允許混淆名稱，因為是以 annotation 的 tag 查找而非 method 名稱，且 @MockRequest.mockerClass 會隨混淆一併改寫
-if class com.noelsung.retrofitmocker.MockCallAdapterFactory
-keepclasseswithmembers,allowobfuscation class * {
    <init>();
    @com.noelsung.retrofitmocker.annotations.MockResponse <methods>;
}
