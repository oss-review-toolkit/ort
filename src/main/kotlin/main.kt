data class Person(val name: String, val age: Int? = null)

fun main(args: Array<String>) {
    val persons = listOf(Person("Alice"), Person("Bob", age = 42))
    val oldest = persons.maxBy { it.age ?: 0 }
    println("The oldest is: $oldest")
}
