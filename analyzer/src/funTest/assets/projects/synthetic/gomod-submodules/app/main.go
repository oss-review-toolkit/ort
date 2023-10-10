package main

import (
    "github.com/fatih/color"
    "github.com/oss-review-toolkit/ort/utils"
)

func main() {
    color.New(color.FgCyan).Println(utils.GetText())
}
