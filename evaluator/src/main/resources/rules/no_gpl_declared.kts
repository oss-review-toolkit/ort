// Use this rule like:
//
// $ ort evaluate -i scanner/src/funTest/assets/file-counter-expected-output-for-analyzer-result.yml --rules-resource /rules/no_gpl_declared.kts

val pkgWithGpl = ortResult.analyzer?.result?.packages?.filter { (pkg, _) ->
    pkg.declaredLicenses.any { license ->
        license.startsWith("GPL")
    }
}

// Populate the list of errors to return.
pkgWithGpl?.forEach { (pkg, _) ->
    evalErrors += OrtIssue(source = pkg.id.toString(), message = "This package is declared under a GPL license.")
}
