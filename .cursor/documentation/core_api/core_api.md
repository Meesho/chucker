# Core API

<details>
<summary>Relevant source files</summary>

The following files were used as context for generating this wiki page:

- [CHANGELOG.md](CHANGELOG.md)
- [README.md](README.md)
- [gradle.properties](gradle.properties)
- [library/src/main/kotlin/com/chuckerteam/chucker/api/Chucker.kt](library/src/main/kotlin/com/chuckerteam/chucker/api/Chucker.kt)
- [library/src/main/kotlin/com/chuckerteam/chucker/internal/data/repository/HttpTransactionDatabaseRepository.kt](library/src/main/kotlin/com/chuckerteam/chucker/internal/data/repository/HttpTransactionDatabaseRepository.kt)
- [library/src/main/kotlin/com/chuckerteam/chucker/internal/data/repository/HttpTransactionRepository.kt](library/src/main/kotlin/com/chuckerteam/chucker/internal/data/repository/HttpTransactionRepository.kt)
- [library/src/main/kotlin/com/chuckerteam/chucker/internal/data/room/HttpTransactionDao.kt](library/src/main/kotlin/com/chuckerteam/chucker/internal/data/room/HttpTransactionDao.kt)

</details>



The Core API provides the primary interface for integrating Chucker into Android applications. This includes the main interceptor for capturing HTTP traffic, data collection and management components, and utility methods for interacting with the library programmatically.

For detailed interceptor configuration, see [ChuckerInterceptor](#4.1). For data layer implementation details, see [Data Management](#4.2).

## API Overview

The Chucker Core API consists of three main components that work together to provide HTTP inspection capabilities:

### Core API Architecture

```mermaid
graph TD
    subgraph "Public API Surface"
        CHUCKER["Chucker<br/>Static utility methods"]
        INTERCEPTOR["ChuckerInterceptor<br/>OkHttp integration"] 
        COLLECTOR["ChuckerCollector<br/>Data collection & retention"]
    end
    
    subgraph "Data Layer"
        REPO_INTERFACE["HttpTransactionRepository<br/>Interface"]
        REPO_IMPL["HttpTransactionDatabaseRepository<br/>Room implementation"]
        DAO["HttpTransactionDao<br/>Database operations"]
    end
    
    subgraph "External Integration"
        OKHTTP["OkHttp Client"]
        USER_APP["Android Application"]
    end
    
    USER_APP --> CHUCKER
    USER_APP --> INTERCEPTOR
    USER_APP --> COLLECTOR
    OKHTTP --> INTERCEPTOR
    
    INTERCEPTOR --> COLLECTOR
    COLLECTOR --> REPO_INTERFACE
    REPO_INTERFACE --> REPO_IMPL
    REPO_IMPL --> DAO
    
    CHUCKER --> REPO_INTERFACE
```

Sources: [library/src/main/kotlin/com/chuckerteam/chucker/api/Chucker.kt:1-117](), [README.md:93-128]()

## Component Relationships

The Core API components interact in a layered architecture where the interceptor captures HTTP events, the collector manages data lifecycle, and the repository handles persistence:

```mermaid
sequenceDiagram
    participant App as "Android App"
    participant Interceptor as "ChuckerInterceptor"
    participant Collector as "ChuckerCollector"
    participant Repository as "HttpTransactionRepository"
    participant UI as "Chucker UI"
    
    App->>Interceptor: "HTTP Request"
    Interceptor->>Collector: "recordTransaction()"
    Collector->>Repository: "insertTransaction()"
    Repository->>Repository: "Store in SQLite"
    
    App->>Interceptor: "HTTP Response"
    Interceptor->>Collector: "updateTransaction()"
    Collector->>Repository: "updateTransaction()"
    
    UI->>Repository: "getTransaction(id)"
    Repository->>UI: "HttpTransaction data"
```

Sources: [library/src/main/kotlin/com/chuckerteam/chucker/internal/data/repository/HttpTransactionRepository.kt:1-32](), [README.md:45-51]()

## ChuckerInterceptor

The `ChuckerInterceptor` is the primary entry point for HTTP traffic capture. It integrates with OkHttp clients to intercept and record HTTP requests and responses.

### Basic Usage

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(ChuckerInterceptor(context))
    .build()
```

### Builder Pattern Configuration

```kotlin
val chuckerInterceptor = ChuckerInterceptor.Builder(context)
    .collector(chuckerCollector)
    .maxContentLength(250_000L)
    .redactHeaders("Auth-Token", "Bearer")
    .alwaysReadResponseBody(true)
    .addBodyDecoder(decoder)
    .createShortcut(true)
    .build()
```

**Key Configuration Options:**
- `collector()` - Custom ChuckerCollector instance
- `maxContentLength()` - Maximum body content length before truncation
- `redactHeaders()` - Headers to redact in the UI for security
- `alwaysReadResponseBody()` - Read complete response even if client doesn't consume it
- `addBodyDecoder()` - Custom decoders for binary content
- `createShortcut()` - Create Android dynamic shortcut

Sources: [README.md:106-128]()

## ChuckerCollector

The `ChuckerCollector` manages data collection, retention policies, and notification display. It acts as an intermediary between the interceptor and the data repository.

### Configuration

```kotlin
val chuckerCollector = ChuckerCollector(
    context = this,
    showNotification = true,
    retentionPeriod = RetentionManager.Period.ONE_HOUR
)
```

**Configuration Parameters:**
- `context` - Android application context
- `showNotification` - Toggle visibility of persistent notifications
- `retentionPeriod` - Data retention period for automatic cleanup

Sources: [README.md:96-103]()

## Chucker Utility Class

The `Chucker` object provides static utility methods for programmatic interaction with the library:

### Core Utility Methods

| Method | Purpose | Return Type |
|--------|---------|-------------|
| `getLaunchIntent(context)` | Get intent to launch Chucker UI | `Intent` |
| `dismissNotifications(context)` | Dismiss all Chucker notifications | `Unit` |
| `clearTransactions()` | Clear all stored transaction data | `suspend Unit` |
| `generateHar(context, limit)` | Export transactions as HAR format | `suspend ByteArray` |
| `isOp` | Check if operational (vs no-op) instance | `Boolean` |

### Implementation Details

```mermaid
classDiagram
    class Chucker {
        +isOp: Boolean
        +getLaunchIntent(context: Context): Intent
        +dismissNotifications(context: Context)
        +clearTransactions(): suspend Unit
        +generateHar(context: Context, transactionsLimit: Int): suspend ByteArray
        +createShortcut(context: Context): internal Unit
        +logger: Logger
    }
    
    class Logger {
        +info(message: String, throwable: Throwable?)
        +warn(message: String, throwable: Throwable?)
        +error(message: String, throwable: Throwable?)
    }
    
    Chucker --> Logger: "uses"
```

**Key Features:**
- **Operational Detection**: `isOp` property distinguishes between full library and no-op variant
- **UI Integration**: `getLaunchIntent()` provides direct access to Chucker UI from host apps
- **Data Export**: `generateHar()` enables programmatic export of HTTP transactions
- **Notification Management**: Centralized notification dismissal across the library

Sources: [library/src/main/kotlin/com/chuckerteam/chucker/api/Chucker.kt:24-116]()

## Data Management API

The data layer provides repository pattern interfaces for managing HTTP transaction persistence:

### Repository Interface

The `HttpTransactionRepository` defines the contract for data operations:

| Operation | Method | Purpose |
|-----------|--------|---------|
| **Create** | `insertTransaction(transaction)` | Store new HTTP transaction |
| **Update** | `updateTransaction(transaction)` | Update existing transaction |
| **Query** | `getTransaction(transactionId)` | Get single transaction by ID |
| **Query** | `getSortedTransactionTuples()` | Get all transactions sorted by date |
| **Query** | `getFilteredTransactionTuples(code, path)` | Get filtered transaction list |
| **Cleanup** | `deleteOldTransactions(threshold)` | Remove transactions older than threshold |
| **Cleanup** | `deleteAllTransactions()` | Clear all stored transactions |

### Database Implementation

```mermaid
classDiagram
    class HttpTransactionRepository {
        <<interface>>
        +insertTransaction(transaction: HttpTransaction): suspend Unit
        +updateTransaction(transaction: HttpTransaction): suspend Int
        +getTransaction(id: Long): LiveData~HttpTransaction?~
        +getSortedTransactionTuples(): LiveData~List~HttpTransactionTuple~~
        +getFilteredTransactionTuples(code: String, path: String): LiveData~List~HttpTransactionTuple~~
        +deleteOldTransactions(threshold: Long): suspend Unit
        +deleteAllTransactions(): suspend Unit
    }
    
    class HttpTransactionDatabaseRepository {
        -database: ChuckerDatabase
        +transactionDao: HttpTransactionDao
    }
    
    class HttpTransactionDao {
        <<Room DAO>>
        +insert(transaction: HttpTransaction): suspend Long?
        +update(transaction: HttpTransaction): suspend Int
        +getById(id: Long): LiveData~HttpTransaction?~
        +getSortedTuples(): LiveData~List~HttpTransactionTuple~~
        +getFilteredTuples(codeQuery: String, pathQuery: String): LiveData~List~HttpTransactionTuple~~
        +deleteAll(): suspend Unit
        +deleteBefore(threshold: Long): suspend Unit
    }
    
    HttpTransactionRepository <|-- HttpTransactionDatabaseRepository
    HttpTransactionDatabaseRepository --> HttpTransactionDao
```

**Repository Features:**
- **Room Integration**: Uses Android Architecture Components Room for SQLite operations
- **LiveData Support**: Reactive data queries return `LiveData` for UI observation
- **Coroutine Support**: Async operations use Kotlin coroutines with `suspend` functions
- **Query Flexibility**: Supports filtering by response code and request path

Sources: [library/src/main/kotlin/com/chuckerteam/chucker/internal/data/repository/HttpTransactionRepository.kt:1-32](), [library/src/main/kotlin/com/chuckerteam/chucker/internal/data/repository/HttpTransactionDatabaseRepository.kt:1-48](), [library/src/main/kotlin/com/chuckerteam/chucker/internal/data/room/HttpTransactionDao.kt:1-51]()

## Integration Pattern

The typical integration flow follows this pattern:

```mermaid
graph LR
    subgraph "Developer Integration"
        DEP["Add Gradle Dependencies<br/>debugImplementation library<br/>releaseImplementation library-no-op"]
        SETUP["Create ChuckerInterceptor<br/>Configure OkHttp Client"]
        USE["Make HTTP Requests<br/>Access Chucker UI"]
    end
    
    subgraph "Runtime Behavior"
        INTERCEPT["HTTP Traffic Interception"]
        COLLECT["Data Collection & Storage"]
        DISPLAY["UI Display & Export"]
    end
    
    DEP --> SETUP
    SETUP --> USE
    USE --> INTERCEPT
    INTERCEPT --> COLLECT
    COLLECT --> DISPLAY
```

**Integration Steps:**
1. **Dependency Setup**: Add both operational and no-op variants to Gradle dependencies
2. **Interceptor Configuration**: Create and configure `ChuckerInterceptor` instance
3. **OkHttp Integration**: Add interceptor to OkHttp client builder
4. **Runtime Operation**: Library automatically captures and displays HTTP traffic

Sources: [README.md:34-67]()
