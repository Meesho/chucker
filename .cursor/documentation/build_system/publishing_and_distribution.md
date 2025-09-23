# Publishing and Distribution

<details>
<summary>Relevant source files</summary>

The following files were used as context for generating this wiki page:

- [build.gradle](build.gradle)
- [gradle/gradle-mvn-push.gradle](gradle/gradle-mvn-push.gradle)
- [gradle/wrapper/gradle-wrapper.properties](gradle/wrapper/gradle-wrapper.properties)
- [library-no-op/build.gradle](library-no-op/build.gradle)
- [library/build.gradle](library/build.gradle)
- [library/src/main/kotlin/com/chuckerteam/chucker/internal/ui/BaseChuckerActivity.kt](library/src/main/kotlin/com/chuckerteam/chucker/internal/ui/BaseChuckerActivity.kt)
- [sample/build.gradle](sample/build.gradle)
- [sample/src/main/kotlin/com/chuckerteam/chucker/sample/MainActivity.kt](sample/src/main/kotlin/com/chuckerteam/chucker/sample/MainActivity.kt)

</details>



This document covers how Chucker library artifacts are built, versioned, signed, and published to Maven repositories. It details the dual distribution strategy, version management, and the publishing pipeline that makes Chucker available to Android developers through Maven Central and JFrog Artifactory.

For information about the module structure and build configuration, see [Module Structure](#3.1). For details about the Gradle wrapper that bootstraps the build environment, see [Gradle Wrapper](#3.2).

## Overview

Chucker uses a sophisticated publishing strategy that distributes two distinct artifacts:
- `com.meesho.android.chucker:library` - Full HTTP inspection functionality
- `com.meesho.android.chucker:library-no-op` - No-op stub for production builds

Both artifacts are published to multiple Maven repositories with proper PGP signing for security and authenticity.

### Publishing Architecture

```mermaid
graph TD
    subgraph "Source Modules"
        LIB["library/"]
        NOOP["library-no-op/"]
    end
    
    subgraph "Build Artifacts"
        LIB_AAR["library-release.aar"]
        LIB_SOURCES["library-sources.jar"]
        NOOP_AAR["library-no-op-release.aar"]  
        NOOP_SOURCES["library-no-op-sources.jar"]
        JAVADOC["javadoc.jar"]
    end
    
    subgraph "Version Management"
        GIT_TAG["Git Tag Detection"]
        VERSION_LOGIC["versionName() Function"]
        SNAPSHOT["Branch-SNAPSHOT"]
        RELEASE["Tag Version"]
    end
    
    subgraph "Publishing Targets"
        JFROG_SNAP["JFrog Artifactory\nSnapshot Repository"]
        JFROG_REL["JFrog Artifactory\nRelease Repository"]
        SONATYPE_SNAP["Sonatype Snapshots"]
        SONATYPE_STAGE["Sonatype Staging"]
        MAVEN_CENTRAL["Maven Central"]
    end
    
    subgraph "Security"
        PGP_SIGN["PGP Signing"]
        CREDENTIALS["Repository Credentials"]
    end
    
    LIB --> LIB_AAR
    LIB --> LIB_SOURCES
    NOOP --> NOOP_AAR
    NOOP --> NOOP_SOURCES
    
    GIT_TAG --> VERSION_LOGIC
    VERSION_LOGIC --> SNAPSHOT
    VERSION_LOGIC --> RELEASE
    
    LIB_AAR --> PGP_SIGN
    NOOP_AAR --> PGP_SIGN
    
    PGP_SIGN --> JFROG_SNAP
    PGP_SIGN --> JFROG_REL
    PGP_SIGN --> SONATYPE_SNAP
    PGP_SIGN --> SONATYPE_STAGE
    SONATYPE_STAGE --> MAVEN_CENTRAL
```

Sources: [build.gradle:64-99](), [library/build.gradle:115-156](), [library-no-op/build.gradle:67-108](), [gradle/gradle-mvn-push.gradle:42-118]()

## Version Management

Chucker uses a Git-based versioning strategy implemented in the `versionName()` function within both library modules.

### Versioning Logic

| Condition | Version Format | Example |
|-----------|---------------|---------|
| Tagged commit matching `[0-9.]*[0-9]` | Tag name | `4.0.0` |
| Untagged commit | `{branch}-SNAPSHOT` | `develop-SNAPSHOT` |

The version detection logic:

```kotlin
ext.versionName = { ->
    def currentTag = 'git tag --points-at HEAD'.execute().in.text.toString().trim()
    def currentBranch = 'git rev-parse --abbrev-ref HEAD'.execute().in.text.toString().trim()
    def tagRegex = "[0-9.]*[0-9]"
    if (!currentTag.isEmpty() && currentTag.matches(tagRegex)) {
        return currentTag
    } else {
        return currentBranch + '-SNAPSHOT'
    }
}
```

Sources: [library/build.gradle:96-105](), [library-no-op/build.gradle:48-57]()

## Artifact Generation

Both library modules generate standardized Maven artifacts including AAR files, source JARs, and Javadoc.

### Library Module Artifacts

```mermaid
graph LR
    subgraph "library Module"
        KOTLIN_SRC["Kotlin Sources"]
        ANDROID_RES["Android Resources"]
        DEPS["Dependencies"]
    end
    
    subgraph "Build Process"
        COMPILE["Android Compilation"]
        AAR_GEN["AAR Generation"]
        SOURCES_JAR["androidSourcesJar Task"]
        JAVADOC_JAR["javadocJar Task"]
    end
    
    subgraph "Generated Artifacts"
        RELEASE_AAR["library-release.aar"]
        SRC_JAR["library-sources.jar"]
        DOC_JAR["library-javadoc.jar"]
        POM_XML["Generated POM"]
    end
    
    KOTLIN_SRC --> COMPILE
    ANDROID_RES --> COMPILE
    DEPS --> COMPILE
    COMPILE --> AAR_GEN
    AAR_GEN --> RELEASE_AAR
    KOTLIN_SRC --> SOURCES_JAR
    SOURCES_JAR --> SRC_JAR
    JAVADOC_JAR --> DOC_JAR
    DEPS --> POM_XML
```

The `androidSourcesJar` task packages source files:

```gradle
task androidSourcesJar(type: Jar) {
    archiveClassifier.set('sources')
    from android.sourceSets.main.kotlin.srcDirs
}
```

Sources: [library/build.gradle:110-113](), [library-no-op/build.gradle:62-65](), [gradle/gradle-mvn-push.gradle:31-34]()

## Publishing Targets

Chucker publishes to multiple Maven repositories depending on the artifact version and target environment.

### Repository Configuration

```mermaid
graph TD
    subgraph "JFrog Artifactory"
        JFROG_URL["JFROG_ARTIFACTORY_URL"]
        SNAPSHOT_REPO["SNAPSHOT_REPO_NAME"]
        RELEASE_REPO["RELEASE_REPO_NAME"]
        JFROG_CREDS["JFROG_ARTIFACTORY_USERNAME\nJFROG_ARTIFACTORY_KEY"]
    end
    
    subgraph "Sonatype OSS"
        SNAPSHOT_URL["oss.sonatype.org/content/repositories/snapshots"]
        STAGING_URL["oss.sonatype.org/service/local/staging/deploy/maven2"]
        NEXUS_CREDS["NEXUS_USERNAME\nNEXUS_PASSWORD"]
    end
    
    subgraph "Routing Logic"
        VERSION_CHECK{{"Version ends with\n'-SNAPSHOT'?"}}
        SNAPSHOT_FLOW["Snapshot Publishing"]
        RELEASE_FLOW["Release Publishing"]
    end
    
    VERSION_CHECK -->|Yes| SNAPSHOT_FLOW
    VERSION_CHECK -->|No| RELEASE_FLOW
    
    SNAPSHOT_FLOW --> SNAPSHOT_REPO
    SNAPSHOT_FLOW --> SNAPSHOT_URL
    RELEASE_FLOW --> RELEASE_REPO
    RELEASE_FLOW --> STAGING_URL
```

### JFrog Artifactory Configuration

The artifactory plugin configuration determines repository routing:

```gradle
artifactory {
    contextUrl = project.properties["JFROG_ARTIFACTORY_URL"]
    publish {
        repository {
            repoKey = libraryVersion.endsWith('-SNAPSHOT') ? 
                project.properties["SNAPSHOT_REPO_NAME"] :
                project.properties["RELEASE_REPO_NAME"]
            username = project.properties["JFROG_ARTIFACTORY_USERNAME"]
            password = project.properties["JFROG_ARTIFACTORY_KEY"]
        }
    }
}
```

Sources: [library/build.gradle:138-156](), [library-no-op/build.gradle:90-108](), [gradle/gradle-mvn-push.gradle:43-60]()

## Maven Publication Configuration

Each library module defines Maven publication details including group ID, artifact ID, and POM generation.

### Publication Definition

| Module | Group ID | Artifact ID | Publication Name |
|--------|----------|-------------|------------------|
| library | `com.meesho.android.chucker` | `library` | `aar` |
| library-no-op | `com.meesho.android.chucker` | `library-no-op` | `aar` |

### POM Generation

Both modules generate POM files with dependency information:

```gradle
pom.withXml {
    def dependencies = asNode().appendNode('dependencies')
    configurations.getByName("releaseCompileClasspath")
        .getResolvedConfiguration()
        .getFirstLevelModuleDependencies().each {
        def dependency = dependencies.appendNode('dependency')
        dependency.appendNode('groupId', it.moduleGroup)
        dependency.appendNode('artifactId', it.moduleName)
        dependency.appendNode('version', it.moduleVersion)
    }
}
```

Sources: [library/build.gradle:116-137](), [library-no-op/build.gradle:68-89]()

## PGP Signing and Security

Maven Central releases require PGP signing for authenticity. The `gradle-mvn-push.gradle` script handles signing configuration.

### Signing Configuration

```mermaid
graph TD
    subgraph "Signing Prerequisites"
        SIGNING_KEY["SIGNING_KEY Property"]
        SIGNING_PWD["SIGNING_PWD Property"]
    end
    
    subgraph "Signing Process"
        KEY_CHECK{{"Signing credentials\navailable?"}}
        IN_MEMORY_PGP["useInMemoryPgpKeys()"]
        SIGN_ARTIFACTS["Sign Publications"]
        SKIP_SIGNING["Log: Signing Disabled"]
    end
    
    subgraph "Signed Artifacts"
        SIGNED_AAR["Signed AAR"]
        SIGNED_SOURCES["Signed Sources JAR"]
        SIGNED_JAVADOC["Signed Javadoc JAR"]
        SIGNED_POM["Signed POM"]
    end
    
    SIGNING_KEY --> KEY_CHECK
    SIGNING_PWD --> KEY_CHECK
    KEY_CHECK -->|Yes| IN_MEMORY_PGP
    KEY_CHECK -->|No| SKIP_SIGNING
    IN_MEMORY_PGP --> SIGN_ARTIFACTS
    SIGN_ARTIFACTS --> SIGNED_AAR
    SIGN_ARTIFACTS --> SIGNED_SOURCES
    SIGN_ARTIFACTS --> SIGNED_JAVADOC
    SIGN_ARTIFACTS --> SIGNED_POM
```

The signing logic conditionally applies PGP signing:

```gradle
def signingKey = findProperty("SIGNING_KEY")
def signingPwd = findProperty("SIGNING_PWD")
if (signingKey && signingPwd) {
    signing {
        useInMemoryPgpKeys(signingKey, signingPwd)
        sign publishing.publications.release
    }
} else {
    logger.info("Signing Disable as the PGP key was not found")
}
```

Sources: [gradle/gradle-mvn-push.gradle:108-118]()

## Dual Distribution Strategy

Chucker's key architectural decision is publishing two variants: a full-featured library and a no-op stub for production builds.

### Distribution Comparison

```mermaid
graph TD
    subgraph "Development Integration"
        DEV_APP["Android App\nDebug Build"]
        DEBUG_IMPL["debugImplementation\n'com.meesho.android.chucker:library'"]
    end
    
    subgraph "Production Integration"
        PROD_APP["Android App\nRelease Build"]
        RELEASE_IMPL["releaseImplementation\n'com.meesho.android.chucker:library-no-op'"]
    end
    
    subgraph "Full Library Features"
        HTTP_INTERCEPT["HTTP Traffic Interception"]
        UI_ACTIVITIES["Chucker UI Activities"]
        DATABASE["SQLite Transaction Storage"]
        NOTIFICATIONS["Persistent Notifications"]
    end
    
    subgraph "No-Op Library Features"
        STUB_INTERCEPT["Stub ChuckerInterceptor"]
        NO_UI["No UI Components"]
        NO_STORAGE["No Data Storage"]
        MINIMAL_SIZE["Minimal APK Impact"]
    end
    
    DEV_APP --> DEBUG_IMPL
    DEBUG_IMPL --> HTTP_INTERCEPT
    DEBUG_IMPL --> UI_ACTIVITIES
    DEBUG_IMPL --> DATABASE
    DEBUG_IMPL --> NOTIFICATIONS
    
    PROD_APP --> RELEASE_IMPL
    RELEASE_IMPL --> STUB_INTERCEPT
    RELEASE_IMPL --> NO_UI
    RELEASE_IMPL --> NO_STORAGE
    RELEASE_IMPL --> MINIMAL_SIZE
```

### Sample App Configuration

The sample application demonstrates this dual dependency approach:

```gradle
dependencies {
    debugImplementation project(':library')
    releaseImplementation project(':library-no-op')
    // ... other dependencies
}
```

This ensures that:
- Debug builds include full Chucker functionality for development
- Release builds use the no-op implementation with minimal overhead
- Same API surface across both variants maintains compatibility

Sources: [sample/build.gradle:70-71](), [library/build.gradle:1-56](), [library-no-op/build.gradle:1-46]()
