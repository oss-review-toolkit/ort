package main

import "github.com/fatih/color"
import "rsc.io/quote/v3"

func main() {
	c := color.New(color.FgCyan).Add(color.Underline)
	c.Println("Prints cyan text with an underline.")

	c.Println(quote.HelloV3())
}
