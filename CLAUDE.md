# Happy Foundation KMP

A Kotlin Multiplatform foundation library providing core utilities, coroutine helpers, and serialization support.

## Build Commands

```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :happy-foundation:build
./gradlew :happy-foundation-coroutines:build
./gradlew :happy-foundation-serialization:build

# Run all tests
./gradlew allTests

# Run tests for specific platforms
./gradlew jvmTest              # JVM tests
./gradlew iosSimulatorArm64Test # iOS Simulator tests
./gradlew macosArm64Test        # macOS tests

# Clean build
./gradlew clean

# Check dependencies
./gradlew dependencies

# Linting
./gradlew spotlessCheck    # Check formatting
./gradlew spotlessApply    # Auto-fix formatting
```

## Project Structure

```
kmp-foundation-kotlin/
├── happy-foundation/           # Core utilities module
│   └── src/
│       ├── commonMain/         # Shared Kotlin code
│       ├── androidMain/        # Android-specific implementations
│       ├── appleMain/          # iOS/macOS shared code
│       ├── jvmMain/            # JVM-specific implementations
│       └── commonTest/         # Shared tests
├── happy-foundation-coroutines/ # Coroutine utilities
│   └── src/
│       ├── commonMain/
│       ├── androidMain/
│       ├── appleMain/
│       └── commonTest/
├── happy-foundation-serialization/ # kotlinx.serialization helpers
│   └── src/
│       ├── commonMain/
│       ├── androidMain/
│       └── appleMain/
└── gradle/
    └── libs.versions.toml      # Dependency version catalog
```

## Architecture

### Platform Targets
- **Android**: API 35+ (compile SDK 36)
- **iOS**: iosArm64, iosSimulatorArm64
- **macOS**: macosArm64
- **JVM**: For desktop and testing

### Module Dependencies
```
happy-foundation-coroutines ──depends-on──> happy-foundation
happy-foundation-serialization ──depends-on──> happy-foundation
```

### Key Technologies
- **Kotlin**: 2.3.0
- **JVM Target**: 17
- **Coroutines**: kotlinx-coroutines 1.10.2
- **Serialization**: kotlinx-serialization 1.9.0
- **Datetime**: kotlinx-datetime 0.7.1
- **Logging**: Kermit 2.0.8
- **URI handling**: uri-kmp 0.0.21

## Code Style

Follows official Kotlin code style with project-specific settings:

- **Kotlin files**: 4-space indentation
- **XML/JSON/YAML**: 2-space indentation
- **Line endings**: LF (Unix-style)
- **Trailing whitespace**: Trimmed (except in Markdown)
- **Final newline**: Required

EditorConfig is defined at project root.

### Linting

Uses [Spotless](https://github.com/diffplug/spotless) with ktlint for code formatting:

- **Kotlin/Kts**: ktlint with default rules (trailing commas, expression bodies, etc.)
- **JSON**: 2-space indentation
- **YAML**: Jackson formatter

Run `./gradlew spotlessCheck` before committing. Use `./gradlew spotlessApply` to auto-fix.

### Swift Interop
Use `@ObjCName` annotation for public interfaces to provide Swift-friendly names:
```kotlin
@ObjCName("HappyIdentifiable")
interface Identifiable {
    val id: String
}
```

## Key Patterns

### happy-foundation
Core interfaces and extensions:
- `Identifiable` - Interface for entities with string identifiers
- `TypeIdentifiable` - Type-based identification
- `Boolean.Extensions` - Boolean utility extensions
- `String.Extensions` - String manipulation utilities (platform-specific implementations)
- `Collection.Extensions` - Collection utilities

Package: `com.happycodelucky.foundation`

### happy-foundation-coroutines
Coroutine utilities:
- `DerivedStateFlow` - StateFlow that respects "distinct until changed" semantics
- `FlowCoalesce` - Flow coalescing utilities
- `CoroutineReusableSharedJob` - Reusable shared job management

Package: `com.happycodelucky.coroutines`

### happy-foundation-serialization
Custom kotlinx.serialization serializers:
- `Base64Serializer` - Base64 encoding/decoding
- `HexColorSerializer` - Hex color string handling
- `DurationStringSerializer` - ISO 8601 duration strings
- `LocalDateISO8601Serializer` / `LocalDateTimeISO8601Serializer` - Date/time handling
- `ImmutableListSerializer` / `ImmutableSetSerializer` / `ImmutableMapSerializer` - Immutable collections

Package: `com.happycodelucky.serializers`

## Testing

Tests are located in `src/commonTest/` for each module. JVM tests provide the fastest feedback loop.

```bash
# Quick test cycle (JVM only)
./gradlew jvmTest

# Full multiplatform test
./gradlew allTests
```

Current test coverage is focused on:
- `CoroutineReusableSharedJobTests` - Reusable job behavior
- `FlowCoalesceTests` - Flow coalescing logic

## Dependencies

All versions managed in `gradle/libs.versions.toml`. Key patterns:
- Use BOM for coroutines: `implementation(project.dependencies.platform(libs.kotlinx.coroutines.bom))`
- Internal module deps: `implementation(project.dependencies.project(":happy-foundation"))`

## Experimental APIs

The project opts into several experimental Kotlin APIs:
- `kotlin.experimental.ExperimentalObjCName` - Swift name customization
- `kotlinx.atomicfu.ExperimentalAtomicApi` - Atomic operations (coroutines module)
- `ExperimentalCoroutinesApi` - Coroutines experimental features
