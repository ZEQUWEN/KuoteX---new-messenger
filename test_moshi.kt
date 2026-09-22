import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.ui.botapi.CustomBot

fun main() {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val bot = CustomBot(
        id = "test_bot",
        name = "Test Bot",
        description = "A test bot",
        category = "Custom",
        longDescription = "A long description",
        oauthToken = "token"
    )
    val adapter = moshi.adapter(CustomBot::class.java)
    println(adapter.toJson(bot))
}
