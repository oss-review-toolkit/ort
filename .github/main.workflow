workflow "Automatic Rebase" {
  on = "issue_comment"
  resolves = ["Rebase"]
}

action "Rebase" {
  uses = "cirrus-actions/rebase@latest"
}
