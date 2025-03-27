package main

import (
	"github.com/klauspost/shutdown2"
)

func main() {
	shutdown.OnSignal(0, os.Interrupt, syscall.SIGTERM)
}
