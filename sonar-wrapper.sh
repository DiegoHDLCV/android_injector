#!/bin/bash

# Load token from gradle.properties.local
if [ -f "gradle.properties.local" ]; then
    export SONAR_TOKEN_LOCAL=$(grep "^SONAR_TOKEN_LOCAL=" gradle.properties.local | cut -d'=' -f2-)
    echo "✓ Token loaded from gradle.properties.local"
fi

# Run the sonar task
if [ -z "$1" ]; then
    echo "Usage: $0 sonarInjector|sonarKeyReceiver|sonar"
    echo "Example: $0 sonarInjector"
    exit 1
fi

TASK="$1"

# Determine which module is being analyzed
case "$TASK" in
    sonarInjector)
        echo "Analyzing INJECTOR module with SonarQube..."
        ./gradlew sonar \
            -DsonarModule=injector \
            -Dsonar.token="${SONAR_TOKEN_LOCAL}"
        ;;
    sonarKeyReceiver)
        echo "Analyzing KEYRECEIVER module with SonarQube..."
        ./gradlew sonar \
            -DsonarModule=keyreceiver \
            -Dsonar.token="${SONAR_TOKEN_LOCAL}"
        ;;
    sonar)
        echo "Analyzing all modules with SonarQube..."
        ./gradlew sonar \
            -Dsonar.token="${SONAR_TOKEN_LOCAL}"
        ;;
    *)
        echo "Unknown task: $TASK"
        echo "Usage: $0 sonarInjector|sonarKeyReceiver|sonar"
        exit 1
        ;;
esac
