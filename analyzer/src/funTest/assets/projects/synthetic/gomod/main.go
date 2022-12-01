package main

import (
    "fmt"
	"strings"

    "github.com/fatih/color"
    "github.com/pborman/uuid"
)

func main() {
	c := color.New(color.FgCyan).Add(color.Underline)
	c.Println("Prints cyan text with an underline.")

    uuidWithHyphen := uuid.NewRandom()
    uuidWithoutHypen := strings.Replace(uuidWithHyphen.String(), "-", "", -1)
    fmt.Println(uuidWithoutHypen)
}
