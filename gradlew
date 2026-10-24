#!/bin/sh
# Minimal Gradle wrapper launcher (works in Termux). Needs gradle/wrapper/gradle-wrapper.jar,
# which setup-termux.sh generates for you.
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
  echo "gradle-wrapper.jar is missing. Run:  sh setup-termux.sh" >&2
  exit 1
fi
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD=java
fi
exec "$JAVACMD" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew \
  -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
