package main

import (
    "testing"

    "github.com/stretchr/testify/assert"
)

func TestSomething(t *testing.T) {
    assert.Equal(t, 1, 1, "they should be equal")
    assert.NotEqual(t, 1, 2, "they should not be equal")
}
