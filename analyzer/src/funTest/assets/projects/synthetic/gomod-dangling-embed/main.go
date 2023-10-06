package main

import (
    _ "embed"
    "fmt"
)

//go:generate touch file.txt
var(
    //go:embed file.txt
    file string
)

func main() {
    fmt.Println(file)
    fmt.Println("hello world")
}
