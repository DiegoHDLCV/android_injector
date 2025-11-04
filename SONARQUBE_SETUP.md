# SonarQube Configuration Guide

## Overview

SonarQube has been integrated into the android_injector project to provide code quality analysis for both the **Injector** and **KeyReceiver** modules. The **dev_injector** module is completely excluded from analysis.

### Key Features

- ✅ **Separate Analysis**: Injector and KeyReceiver modules are analyzed separately in SonarQube as distinct projects
- ✅ **Flavor Support**: Each module now has three flavors (dev, qa, prod) for multi-environment builds
- ✅ **Code Coverage**: JaCoCo integration for unit test coverage analysis
- ✅ **Lint Integration**: Android Lint reports are included in SonarQube analysis
- ✅ **CI/CD Pipeline**: GitHub Actions workflow for automated analysis
- ✅ **Excluded Modules**: dev_injector is completely excluded from analysis

## Configuration Details

### Server Information

- **SonarQube Server**: `http://100.65.127.12:9000`
- **Injector Project Key**: `android_injector_injector`
- **KeyReceiver Project Key**: `android_injector_keyreceiver`

## Quick Start

1. **Edit your token file**:
```bash
nano gradle.properties.local  # Add your SonarQube token
```

2. **Generate reports**:
```bash
./generate-reports.sh
```

3. **Run SonarQube analysis**:
```bash
./sonar-wrapper.sh sonarInjector      # Analyze injector module
./sonar-wrapper.sh sonarKeyReceiver   # Analyze keyreceiver module
```

4. **View results**: `http://100.65.127.12:9000`

---

### Module Flavors

Both application modules (injector and keyreceiver) now support three flavors:

| Flavor | Suffix | Use Case |
|--------|--------|----------|
| `dev` | `.dev` | Development environment |
| `qa` | `.qa` | Quality assurance environment |
| `prod` | (none) | Production environment |

#### Building a Specific Flavor

```bash
# Build dev flavor
./gradlew :injector:assembleDevDebug
./gradlew :keyreceiver:assembleDevDebug

# Build qa flavor
./gradlew :injector:assembleQaDebug
./gradlew :keyreceiver:assembleQaDebug

# Build prod flavor (default)
./gradlew :injector:assembleProdDebug
./gradlew :keyreceiver:assembleProdDebug
```

## Setup Instructions

### 1. Get Your SonarQube Token

1. Go to `http://100.65.127.12:9000`
2. Log in to your SonarQube account
3. Navigate to **User Profile** → **Security** → **Generate Tokens**
4. Generate a new token and copy it

### 2. Configure Local Token

The `gradle.properties.local` file is used to store your SonarQube token locally.

1. Edit `gradle.properties.local` and add your token:
```properties
SONAR_TOKEN_LOCAL=your_actual_token_here
```

2. The file is already ignored by git (.gitignore) and should never be committed.

**Important**: `gradle.properties.local` is ignored by git and should never be committed.

## Running Analysis

### Generate Reports First

Use the provided script to generate all reports (Lint, JaCoCo, etc.):

```bash
./generate-reports.sh
```

This script will:
1. Generate Lint reports for both modules (prod flavor)
2. Run unit tests
3. Generate JaCoCo coverage reports
4. Display the locations of all generated reports

### Run SonarQube Analysis

After generating reports, use the `sonar-wrapper.sh` script which automatically loads your token from `gradle.properties.local`:

#### Analyze Injector Module Only

```bash
./sonar-wrapper.sh sonarInjector
```

#### Analyze KeyReceiver Module Only

```bash
./sonar-wrapper.sh sonarKeyReceiver
```

#### Analyze Both Modules (Combined)

```bash
./sonar-wrapper.sh sonar
```

**Note**: The `sonar-wrapper.sh` script automatically reads your token from `gradle.properties.local`, so you don't need to set environment variables manually.

### CI/CD Pipeline

The GitHub Actions workflow (`.github/workflows/android_ci.yml`) automatically:

1. Builds the project
2. Generates Lint and JaCoCo reports
3. Runs SonarQube analysis for both modules
4. Performs OWASP Dependency-Check
5. Comments on PRs with analysis results
6. Uploads test reports as artifacts

**Note**: Ensure the `SONAR_TOKEN_LOCAL` secret is configured in your GitHub Actions settings.

## Report Locations

After running the analysis, you can find the reports at:

### Injector Module
- **Lint Report**: `./injector/build/reports/lint-results-debug.html`
- **JaCoCo Coverage (HTML)**: `./injector/build/reports/jacoco/testDebugUnitTest/html/index.html`
- **JaCoCo Coverage (XML)**: `./injector/build/reports/jacoco/testDebugUnitTest/testDebugUnitTest.xml`

### KeyReceiver Module
- **Lint Report**: `./keyreceiver/build/reports/lint-results-debug.html`
- **JaCoCo Coverage (HTML)**: `./keyreceiver/build/reports/jacoco/testDebugUnitTest/html/index.html`
- **JaCoCo Coverage (XML)**: `./keyreceiver/build/reports/jacoco/testDebugUnitTest/testDebugUnitTest.xml`

## Code Exclusions

The following are excluded from SonarQube analysis:

### Entire Modules
- `dev_injector` - Development-only module

### File Patterns
- Generated files (R.class, BuildConfig, etc.)
- Test files
- Data models (model layer)
- UI components and themes
- Dependency Injection modules
- Hardware SDK wrappers
- Data Access Objects (DAOs)

## Testing Integration

Both modules now include comprehensive testing dependencies:

- **JUnit 4**: Unit testing framework
- **Mockito & Mockk**: Mocking libraries
- **Kotlinx Coroutines Test**: Coroutine testing utilities
- **Turbine**: Flow testing library
- **Android Architecture Core Testing**: LiveData testing utilities
- **Google Truth**: Assertion library

Example test configuration is available in each module's `build.gradle.kts` file.

## Troubleshooting

### Token Authentication Fails

1. Verify your token is correct:
```bash
echo $SONAR_TOKEN_LOCAL
```

2. Make sure the token is not expired. Generate a new one if needed.

3. Ensure the SonarQube server is accessible:
```bash
curl -u admin:password http://100.65.127.12:9000/api/system/status
```

### Reports Not Generated

1. Check that unit tests pass:
```bash
./gradlew :injector:testDebugUnitTest
./gradlew :keyreceiver:testDebugUnitTest
```

2. Generate reports manually:
```bash
./generate-reports.sh
```

### Build Fails with Flavor Error

If you see an error like "Cannot locate tasks that match ':injector:compileDebugSources'", remember that flavor tasks are now required:

```bash
# Correct (uses prod flavor by default)
./gradlew :injector:compileProdDebugSources

# Or specify flavor explicitly
./gradlew :injector:compileDevDebugSources
```

## Files Modified/Created

### Modified Files
- `gradle/libs.versions.toml` - Added SonarQube, JaCoCo, and testing libraries
- `build.gradle.kts` - Added SonarQube configuration and custom analysis tasks
- `injector/build.gradle.kts` - Added flavors, JaCoCo, and testing setup
- `keyreceiver/build.gradle.kts` - Added flavors, JaCoCo, and testing setup

### Created Files
- `generate-reports.sh` - Script to generate all reports
- `gradle.properties.local.example` - Template for local SonarQube token
- `.github/workflows/android_ci.yml` - CI/CD pipeline for automated analysis
- `SONARQUBE_SETUP.md` - This documentation file

## Additional Resources

- [SonarQube Documentation](https://docs.sonarqube.org/)
- [SonarQube Gradle Plugin](https://docs.sonarqube.org/latest/analysis/scan/sonarscanner-for-gradle/)
- [JaCoCo Documentation](https://www.jacoco.org/jacoco/trunk/doc/index.html)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)

## Support

For issues or questions regarding SonarQube setup, consult the SonarQube server administrator at `http://100.65.127.12:9000` or contact the development team.
