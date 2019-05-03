// Use this rule like:
//
// $ ort evaluate -i scanner/src/funTest/assets/file-counter-expected-output-for-analyzer-result.yml --rules-resource /rules/no_gpl_declared.kts

// Define a custom rule matcher.
fun PackageRule.LicenseRule.isGpl() =
    object : RuleMatcher {
        override val description = "isGpl($license)"

        override fun matches() = license.contains("GPL")
    }

// Define the rule set.
val ruleSet = ruleSet(ortResult) {

    // Define a rule that is executed for each package.
    packageRule("NO_GPL") {

        // Define a rule that is executed for each license of the package.
        licenseRule("NO_GPL", LicenseView.All) {
            require {
                +isGpl()
            }

            error("The package '${pkg.id.toCoordinates()}' has the ${licenseSource.name} license '$license'.")
        }
    }
}

// Populate the list of errors to return.
ruleViolations += ruleSet.violations
