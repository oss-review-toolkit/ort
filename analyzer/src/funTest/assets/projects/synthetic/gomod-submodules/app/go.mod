module app

go 1.19

require (
	github.com/fatih/color v1.15.0
	github.com/oss-review-toolkit/ort/utils v1.2.3
)

require (
	github.com/google/uuid v1.0.0 // indirect
	github.com/mattn/go-colorable v0.1.13 // indirect
	github.com/mattn/go-isatty v0.0.17 // indirect
	github.com/pborman/uuid v1.2.1 // indirect
	golang.org/x/sys v0.6.0 // indirect
)

replace github.com/oss-review-toolkit/ort/utils => ../utils
