module github.com/oss-review-toolkit/ort/gomod-synthetic-test-project

go 1.18

require (
	github.com/fatih/color v1.13.0
	github.com/hashicorp/go-secure-stdlib/parseutil v0.1.6
	github.com/pborman/uuid v1.2.1
	github.com/stretchr/testify v1.7.1
)

require (
	github.com/davecgh/go-spew v1.1.0 // indirect
	github.com/google/uuid v1.0.0 // indirect
	github.com/hashicorp/go-secure-stdlib/strutil v0.1.1 // indirect
	github.com/hashicorp/go-sockaddr v1.0.2 // indirect
	github.com/mattn/go-colorable v0.1.12 // indirect
	github.com/mattn/go-isatty v0.0.14 // indirect
	github.com/mitchellh/mapstructure v1.4.1 // indirect
	github.com/pmezard/go-difflib v1.0.0 // indirect
	github.com/ryanuber/go-glob v1.0.0 // indirect
	golang.org/x/sys v0.0.0-20220610221304-9f5ed59c137d // indirect
	gopkg.in/yaml.v3 v3.0.1 // indirect
)

replace (
	github.com/davecgh/go-spew => github.com/atomtree/go-spew v1.1.0
	github.com/stretchr/testify => github.com/stretchr/testify v1.7.2
)
