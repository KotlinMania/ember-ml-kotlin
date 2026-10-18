// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "SwiftTestHarness",
    platforms: [.macOS(.v15)],
    dependencies: [
        .package(name: "EmberMl", path: "../build/SPMPackage/macosArm64/Debug")
    ],
    targets: [
        .testTarget(
            name: "SwiftTestHarnessTests",
            dependencies: [
                .product(name: "EmberMlLibrary", package: "EmberMl")
            ],
            linkerSettings: [
                .unsafeFlags([
                    "-L", "../build/swift-test",
                    "-lEmberMl",
                ]),
            ]
        ),
    ]
)
