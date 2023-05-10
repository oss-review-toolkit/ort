module github.com/oss-review-toolkit/ort/gomod-workspaces/other-module

go 1.18

require github.com/fatih/color v1.13.0

require (
	github.com/mattn/go-colorable v0.1.12 // indirect
	github.com/mattn/go-isatty v0.0.14 // indirect
	golang.org/x/sys v0.0.0-20220610221304-9f5ed59c137d // indirect
)

replace (
	github.com/davecgh/go-spew => github.com/atomtree/go-spew v1.1.0
	github.com/stretchr/testify => github.com/stretchr/testify v1.7.2
)
