# Overview

<details>
<summary>Relevant source files</summary>

The following files were used as context for generating this wiki page:

- [CHANGELOG.md](CHANGELOG.md)
- [README.md](README.md)
- [build.gradle](build.gradle)
- [gradle.properties](gradle.properties)
- [gradle/wrapper/gradle-wrapper.properties](gradle/wrapper/gradle-wrapper.properties)
- [library-no-op/build.gradle](library-no-op/build.gradle)
- [library/build.gradle](library/build.gradle)
- [library/src/main/kotlin/com/chuckerteam/chucker/internal/ui/BaseChuckerActivity.kt](library/src/main/kotlin/com/chuckerteam/chucker/internal/ui/BaseChuckerActivity.kt)
- [sample/build.gradle](sample/build.gradle)
- [sample/src/main/kotlin/com/chuckerteam/chucker/sample/MainActivity.kt](sample/src/main/kotlin/com/chuckerteam/chucker/sample/MainActivity.kt)

</details>



## Purpose and Scope

This document provides a high-level overview of Chucker, an HTTP inspection library for Android applications. Chucker simplifies the debugging process by intercepting and persisting HTTP(S) requests/responses fired by Android applications, providing a comprehensive UI for inspecting network traffic during development.

This overview covers the core architecture, key components, module structure, and distribution strategy. For detailed integration instructions, see [Getting Started](#2). For build system specifics, see [Build System](#3). For UI component details, see [User Interface](#5).

## System Architecture

Chucker implements a multi-layered architecture that intercepts HTTP traffic through OkHttp and provides both data persistence and user interface capabilities.

### Core Architecture Flow

```mermaid
graph TB
    subgraph "Client Integration"
        APP["Android Application"]
        OKHTTP["OkHttpClient"]
        INTERCEPTOR["ChuckerInterceptor"]
    end
    
    subgraph "Data Collection Layer"
        COLLECTOR["ChuckerCollector"]
        RETENTION["RetentionManager"]
        REPO["HttpTransactionRepository"]
    end
    
    subgraph "Persistence Layer"
        DAO["HttpTransactionDao"]
        ROOM_DB["Room Database"]
        SQLITE[("SQLite Storage")]
    end
    
    subgraph "UI Layer"
        MAIN_ACTIVITY["MainActivity"]
        TRANSACTION_ACTIVITY["TransactionActivity"]
        PAYLOAD_FRAGMENT["TransactionPayloadFragment"]
    end
    
    subgraph "Distribution Strategy"
        LIBRARY["library module<br/>(Full Implementation)"]
        NO_OP["library-no-op module<br/>(Empty Implementation)"]
    end
    
    APP --> OKHTTP
    OKHTTP --> INTERCEPTOR
    INTERCEPTOR --> COLLECTOR
    COLLECTOR --> RETENTION
    COLLECTOR --> REPO
    REPO --> DAO
    DAO --> ROOM_DB
    ROOM_DB --> SQLITE
    
    REPO --> MAIN_ACTIVITY
    MAIN_ACTIVITY --> TRANSACTION_ACTIVITY
    TRANSACTION_ACTIVITY --> PAYLOAD_FRAGMENT
    
    INTERCEPTOR -.->|"debugImplementation"| LIBRARY
    INTERCEPTOR -.->|"releaseImplementation"| NO_OP
```

Sources: [README.md:24-51](), [library/src/main/kotlin/com/chuckerteam/chucker/internal/ui/BaseChuckerActivity.kt:15-20]()

### Module Structure and Dependencies

```mermaid
graph TD
    subgraph "Root Project Configuration"
        ROOT_BUILD["build.gradle<br/>Global Dependencies & Plugins"]
        GRADLE_PROPS["gradle.properties<br/>VERSION_NAME, GROUP"]
        GRADLE_WRAPPER["gradle-wrapper.properties<br/>Gradle Distribution"]
    end
    
    subgraph "Library Modules"
        LIB_MODULE["library/<br/>Full Chucker Implementation"]
        NOOP_MODULE["library-no-op/<br/>Stub Implementation"]
        
        LIB_BUILD["library/build.gradle<br/>Android Library + Publishing"]
        NOOP_BUILD["library-no-op/build.gradle<br/>Minimal Dependencies"]
    end
    
    subgraph "Sample Application"
        SAMPLE_MODULE["sample/<br/>Demo Application"]
        SAMPLE_BUILD["sample/build.gradle<br/>Debug/Release Variants"]
    end
    
    subgraph "Key Dependencies"
        OKHTTP_DEP["OkHttp 4.9.0"]
        ROOM_DEP["Room 2.6.1"]
        MATERIAL_DEP["Material Components"]
        KOTLIN_DEP["Kotlin 1.9.23"]
    end
    
    ROOT_BUILD --> LIB_BUILD
    ROOT_BUILD --> NOOP_BUILD
    ROOT_BUILD --> SAMPLE_BUILD
    
    LIB_MODULE --> LIB_BUILD
    NOOP_MODULE --> NOOP_BUILD
    SAMPLE_MODULE --> SAMPLE_BUILD
    
    LIB_BUILD --> OKHTTP_DEP
    LIB_BUILD --> ROOM_DEP
    LIB_BUILD --> MATERIAL_DEP
    LIB_BUILD --> KOTLIN_DEP
    
    NOOP_BUILD --> OKHTTP_DEP
    NOOP_BUILD --> KOTLIN_DEP
    
    SAMPLE_BUILD -.->|"debugImplementation"| LIB_MODULE
    SAMPLE_BUILD -.->|"releaseImplementation"| NOOP_MODULE
```

Sources: [build.gradle:1-118](), [library/build.gradle:1-157](), [library-no-op/build.gradle:1-109](), [sample/build.gradle:69-72]()

## Key Components

### ChuckerInterceptor
The primary entry point for developers, `ChuckerInterceptor` implements OkHttp's `Interceptor` interface to capture HTTP traffic. It can be configured with various options including header redaction, body size limits, and custom decoders.

### ChuckerCollector  
Manages data collection, retention policies, and notification visibility. The collector handles the lifecycle of HTTP transaction data and coordinates with the retention manager to clean up old transactions.

### HttpTransactionRepository
Provides the data access layer for HTTP transactions, abstracting database operations and providing a clean API for both the UI components and the interceptor to interact with stored transaction data.

### UI Components
- `MainActivity`: Displays the list of HTTP transactions
- `TransactionActivity`: Shows detailed request/response information
- `BaseChuckerActivity`: Common base class handling window insets and lifecycle

Sources: [README.md:45-51](), [README.md:95-128](), [library/src/main/kotlin/com/chuckerteam/chucker/internal/ui/BaseChuckerActivity.kt:15-72]()

## Distribution Strategy

Chucker employs a dual-artifact distribution strategy to ensure production safety:

| Artifact | Build Type | Implementation | Purpose |
|----------|------------|----------------|---------|
| `library` | `debugImplementation` | Full functionality | Development/debugging |
| `library-no-op` | `releaseImplementation` | Empty stubs | Production builds |

This approach ensures that Chucker code is completely absent from release builds while providing full functionality during development.

```mermaid
graph LR
    subgraph "Development Workflow"
        DEV_BUILD["Debug Build"]
        FULL_LIB["library<br/>Full Implementation"]
        HTTP_CAPTURE["HTTP Traffic Capture"]
        UI_DISPLAY["UI Display & Inspection"]
    end
    
    subgraph "Production Workflow"
        PROD_BUILD["Release Build"]
        NO_OP_LIB["library-no-op<br/>Empty Stubs"]
        NO_IMPACT["Zero Runtime Impact"]
    end
    
    subgraph "Publishing Targets"
        MAVEN_CENTRAL["Maven Central<br/>Stable Releases"]
        JFROG["JFrog Artifactory<br/>Snapshots"]
    end
    
    DEV_BUILD --> FULL_LIB
    FULL_LIB --> HTTP_CAPTURE
    HTTP_CAPTURE --> UI_DISPLAY
    
    PROD_BUILD --> NO_OP_LIB
    NO_OP_LIB --> NO_IMPACT
    
    FULL_LIB --> MAVEN_CENTRAL
    FULL_LIB --> JFROG
    NO_OP_LIB --> MAVEN_CENTRAL
    NO_OP_LIB --> JFROG
```

Sources: [README.md:36-43](), [library/build.gradle:107-156](), [library-no-op/build.gradle:59-108](), [sample/build.gradle:69-72]()

## Build and Publishing System

The project uses a sophisticated Gradle-based build system with automated publishing to multiple repositories:

### Version Management
- Version information stored in [gradle.properties:20-23]()
- Current version: `4.0.0-SNAPSHOT`
- Group ID: `com.github.chuckerteam.chucker` (updated to `com.meesho.android.chucker` in build files)

### Publishing Configuration
- **Snapshot builds**: Published to JFrog Artifactory on every `develop` branch push
- **Release builds**: Published to Maven Central via staging repositories  
- **Artifacts**: Both `library` and `library-no-op` variants with sources

### Development Requirements
- **Minimum SDK**: 21 (API level 21)
- **Target SDK**: 35 (API level 35) 
- **Kotlin**: 1.9.23
- **Java**: 17 (source/target compatibility)
- **OkHttp**: 4.9.0 minimum

Sources: [gradle.properties:20-31](), [build.gradle:113-116](), [library/build.gradle:17-25](), [CHANGELOG.md:212-214]()

## Integration Overview

Chucker integrates into Android projects through standard Gradle dependency declarations and minimal code changes. The typical integration requires adding both debug and release implementations, then configuring the `ChuckerInterceptor` with an existing `OkHttpClient`.

The library automatically handles data persistence, UI presentation, and notification management without requiring additional configuration from the developer.

Sources: [README.md:32-67](), [sample/src/main/kotlin/com/chuckerteam/chucker/sample/MainActivity.kt:17-20]()
