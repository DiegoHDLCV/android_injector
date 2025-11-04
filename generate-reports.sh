#!/bin/bash

# Generate reports script for SonarQube analysis
# This script generates Lint reports, runs unit tests, and creates JaCoCo coverage reports

set -e  # Exit on error

echo "============================================"
echo "Generating reports for SonarQube analysis"
echo "============================================"
echo ""

# Check if gradlew exists
if [ ! -f "./gradlew" ]; then
    echo "Error: gradlew not found. Please run this script from the root of the project."
    exit 1
fi

# Make gradlew executable
chmod +x ./gradlew

# Step 1: Generate Lint reports
echo "Step 1: Generating Lint reports..."
echo "  - Generating Lint report for injector..."
./gradlew :injector:lintDebug --continue 2>&1 | grep -E "(lintDebug|BUILD|FAILURE)" || true

echo "  - Generating Lint report for keyreceiver..."
./gradlew :keyreceiver:lintDebug --continue 2>&1 | grep -E "(lintDebug|BUILD|FAILURE)" || true

echo "✓ Lint reports generated"
echo ""

# Step 2: Run unit tests
echo "Step 2: Running unit tests..."
echo "  - Running tests for injector..."
./gradlew :injector:testDebugUnitTest --continue 2>&1 | grep -E "(testDebugUnitTest|BUILD|Tests|passed)" || true

echo "  - Running tests for keyreceiver..."
./gradlew :keyreceiver:testDebugUnitTest --continue 2>&1 | grep -E "(testDebugUnitTest|BUILD|Tests|passed)" || true

echo "✓ Unit tests completed"
echo ""

# Step 3: Generate JaCoCo reports
echo "Step 3: Generating JaCoCo coverage reports..."
echo "  - Generating JaCoCo report for injector..."
./gradlew :injector:jacocoTestReport --continue 2>&1 | grep -E "(jacocoTestReport|BUILD)" || true

echo "  - Generating JaCoCo report for keyreceiver..."
./gradlew :keyreceiver:jacocoTestReport --continue 2>&1 | grep -E "(jacocoTestReport|BUILD)" || true

echo "✓ JaCoCo reports generated"
echo ""

# Step 4: Report locations
echo "============================================"
echo "Report Locations:"
echo "============================================"
echo ""
echo "Injector Module:"
echo "  - Lint Report: ./injector/build/reports/lint-results-debug.html"
echo "  - JaCoCo Report: ./injector/build/reports/jacoco/testDebugUnitTest/html/index.html"
echo "  - Coverage XML: ./injector/build/reports/jacoco/testDebugUnitTest/testDebugUnitTest.xml"
echo ""
echo "KeyReceiver Module:"
echo "  - Lint Report: ./keyreceiver/build/reports/lint-results-debug.html"
echo "  - JaCoCo Report: ./keyreceiver/build/reports/jacoco/testDebugUnitTest/html/index.html"
echo "  - Coverage XML: ./keyreceiver/build/reports/jacoco/testDebugUnitTest/testDebugUnitTest.xml"
echo ""

# Step 5: Instructions for running SonarQube analysis
echo "============================================"
echo "Next Steps:"
echo "============================================"
echo ""
echo "Make sure you have set the SONAR_TOKEN_LOCAL environment variable:"
echo "  export SONAR_TOKEN_LOCAL=your_sonar_token_here"
echo ""
echo "Or copy gradle.properties.local.example to gradle.properties.local:"
echo "  cp gradle.properties.local.example gradle.properties.local"
echo "  # Then edit and add your token"
echo ""
echo "To analyze the Injector module with SonarQube:"
echo "  ./gradlew sonarInjector"
echo ""
echo "To analyze the KeyReceiver module with SonarQube:"
echo "  ./gradlew sonarKeyReceiver"
echo ""
echo "To analyze both modules together:"
echo "  ./gradlew sonar"
echo ""
echo "View SonarQube results at:"
echo "  http://100.65.127.12:9000"
echo ""

echo "============================================"
echo "✓ Reports generated successfully!"
echo "============================================"
