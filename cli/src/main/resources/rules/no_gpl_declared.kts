// Use this rule like:
//
// $ ort evaluate -i scanner/src/funTest/assets/file-counter-expected-output-for-analyzer-result.yml --rules-resource /rules/no_gpl_declared.kts

ortResult.analyzer?.result?.packages?.all { curatedPkg ->
    curatedPkg.pkg.declaredLicenses.none { license ->
        license == "GPL" || license.startsWith("GPL-")
    }
}
