import kotlinx.browser.*

fun main() {
    window.addEventListener("load", {
        val content = "Hello, ${greet()} 3"
        console.log(content)
        document.getElementById("root")?.textContent = content
    })
}

fun greet() = "world"