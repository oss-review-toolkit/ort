package utils

import "github.com/pborman/uuid"

func GetText() string {
  return uuid.NewRandom().String()
}

