package main

import (
    "fmt"
    "strings"

    "github.com/fatih/color"
)

func main() {
    c := color.New(color.FgCyan).Add(color.Underline)
    c.Println("Prints cyan text with an underline.")
}
