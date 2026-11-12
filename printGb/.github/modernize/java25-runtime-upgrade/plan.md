# Modernization Plan: Java 25 Runtime Upgrade

**Project**: gabryelle-print-agent  
**Project root**: `Print/printGb`

## Technical Framework

- **Language**: Java; Maven compiler target is 17. IntelliJ project SDK is
  already configured as JDK 25.
- **Framework**: No application framework is declared in the assessment or POM.
- **Build tool**: Maven (`pom.xml`)
- **Application type**: Windows desktop/tray application with a local WebSocket
  server and Windows printing integration.
- **Key dependencies**: Java-WebSocket, Jackson, JUnit 5.

## Overview

Upgrade the Java runtime target from 17 to Java 25 LTS while preserving the
print agent's existing desktop, WebSocket, configuration, and printing behavior.
Keep the change scoped to the Java 25 runtime and the configuration/documentation
needed to build and run it consistently.

## Runtime Configuration Surface

The assessment identifies `Print/printGb` as the Java/Maven project. The
assessment's Java-version finding points to `pom.xml:14`; the current
`maven.compiler.release` is 17. The project's `.idea/misc.xml` already selects
JDK 25, while `README.md` still documents JDK 17+ for the runtime and IntelliJ
SDK. Align the Maven compiler target and those runtime instructions with Java
25, retaining the existing Maven packaging and application entry point.

The project has no Maven wrapper, runtime launcher scripts, Dockerfile, or
repository CI workflow. `README.md` documents the shaded executable JAR and
optional `jpackage` Windows installer commands; validate the existing JAR
package without introducing deployment or installer work. `Front end/index.html`
is outside this Maven project and is not a Java build or runtime manifest.

## Scope and Behavior

- Upgrade only the Java runtime/compiler target to Java 25 LTS.
- Preserve existing application behavior and dependencies unless a change is
  strictly required for Java 25 compatibility.
- Do not address other assessment findings, migrate the front end, change
  application features, or add deployment/infrastructure configuration.
- Keep any security/CVE check limited to dependencies affected by this runtime
  upgrade; defer unrelated findings and changes.

## Validation

- Build and run the existing unit tests with JDK 25 (`mvn clean verify`).
- Confirm Maven packages the existing executable shaded JAR at
  `target/gabryelle-print-agent.jar` with its existing main class.
- On a Windows desktop with a graphical session, smoke-test launching the JAR
  on Java 25 and confirm the existing tray/configuration and local-agent startup
  behavior. Do not require a physical print job as part of automated tests.
- Confirm README runtime prerequisites and IntelliJ setup instructions no longer
  imply Java 17 is sufficient.

## Open Questions & Questionnaire

No unresolved decisions. The target Java version (25), project location, and
headless plan-generation preference were supplied in the request. No
infrastructure or deployment changes are in scope.
