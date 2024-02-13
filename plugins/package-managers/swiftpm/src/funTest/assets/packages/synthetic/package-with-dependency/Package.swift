// swift-tools-version:5.7
import PackageDescription

let package = Package(
    name: "package-with-dependency",
    platforms: [
        .macOS(.v10_15)
    ],
    products: [
            .library(name: "PackageWithDep", targets: ["PackageWithDep"])
    ],
    dependencies: [
        .package(url: "https://github.com/Alamofire/Alamofire.git", from: "5.4.4")
    ],
    targets: [
        .target(name: "PackageWithDep", dependencies: [
                .product(name: "Alamofire", package: "Alamofire"),
        ])
    ]
)
