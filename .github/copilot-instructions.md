# Myco Project Rules & Code Analysis Guidelines

## Zero Diagnostic Policy (Strict)
- **Zero Warnings / Zero Errors**: All compiler diagnostics, linter warnings (Android Lint / Detekt / IDE analyzers) must be resolved to 0 before merging or committing.
- **Code Analysis (Visual Studio / CI Style)**:
  - Run `./gradlew lintDebug` to execute full static analysis.
  - SARIF, HTML, and text reports are generated in `app/build/reports/`.
  - Violations of coding standards, deprecated APIs, unhandled resources, or formatting anomalies must be eliminated immediately.

## Kotlin & Android Best Practices
1. **KTX Extensions**: Always prefer Android KTX extension functions (e.g. `SharedPreferences.edit { ... }` instead of `.edit().apply()`).
2. **Time & Durations**: Use Kotlin `Duration` (e.g., `10.milliseconds`, `5.seconds`) instead of legacy raw `Long` millisecond overloads.
3. **Locale-Aware Formatting**: Never use implicit default locales in `String.format` or conversions. Explicitly supply `Locale.getDefault()` (for localized UI) or `Locale.US` / `Locale.ROOT` (for machine-readable keys/APIs).
4. **Exceptions**: When catching an exception that is intentionally ignored, use Kotlin syntax `catch (_: Exception)`.
5. **Modern Android Gradle Plugin (AGP 9+)**:
   - Utilize built-in Kotlin compilation.
   - Do not re-introduce deprecated DSL or disabled feature flags into `gradle.properties`.
6. **Jetpack Compose Guidelines**:
   - Hoist state up to ViewModel where appropriate.
   - Avoid unnecessary recompositions by passing stable lambdas and state.
   - Separate UI concerns into dedicated reusable components.
