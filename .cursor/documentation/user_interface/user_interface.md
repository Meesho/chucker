# User Interface

<details>
<summary>Relevant source files</summary>

The following files were used as context for generating this wiki page:

- [library/src/main/AndroidManifest.xml](library/src/main/AndroidManifest.xml)
- [library/src/main/res/drawable/chucker_arrow_down.xml](library/src/main/res/drawable/chucker_arrow_down.xml)
- [library/src/main/res/layout/chucker_activity_transaction.xml](library/src/main/res/layout/chucker_activity_transaction.xml)
- [library/src/main/res/layout/chucker_fragment_transaction_payload.xml](library/src/main/res/layout/chucker_fragment_transaction_payload.xml)
- [library/src/main/res/values/strings.xml](library/src/main/res/values/strings.xml)

</details>



This document covers Chucker's Android user interface components, activities, and user interactions. The UI provides a comprehensive inspection interface for HTTP transactions captured by the ChuckerInterceptor.

For information about the core API and data management that powers the UI, see [Core API](#4). For details about UI activities and navigation flow, see [Activities and Navigation](#5.1). For transaction display specifics, see [Transaction Display](#5.2). For theming and styling customization, see [Theming and Styling](#5.3).

## UI Architecture Overview

Chucker's user interface follows Android's Activity-based architecture with Material Design components. The UI consists of two main activities that display HTTP transaction data with comprehensive inspection capabilities.

```mermaid
graph TB
    subgraph "Main UI Flow"
        MA["MainActivity<br/>(Transaction List)"]
        TA["TransactionActivity<br/>(Transaction Details)"]
        TPF["TransactionPayloadFragment<br/>(Request/Response Body)"]
        
        MA --> TA
        TA --> TPF
    end
    
    subgraph "Data Layer Integration"
        REPO["HttpTransactionRepository<br/>(Data Access)"]
        
        MA --> REPO
        TA --> REPO
        TPF --> REPO
    end
    
    subgraph "UI Features"
        SEARCH["Search Functionality<br/>(@string/chucker_search)"]
        EXPORT["Export Options<br/>(HAR, Text, cURL)"]
        SHARE["Share Transactions<br/>(Multiple Formats)"]
        CLEAR["Clear Data<br/>(ClearDatabaseService)"]
        
        MA --> SEARCH
        TA --> EXPORT
        TA --> SHARE
        MA --> CLEAR
    end
    
    subgraph "UI Components"
        TOOLBAR["MaterialToolbar<br/>(Navigation & Actions)"]
        TABS["TabLayout<br/>(Overview/Request/Response)"]
        RECYCLER["RecyclerView<br/>(Transaction & Payload Lists)"]
        JSON["JsonViewLayout<br/>(JSON Formatting)"]
        
        TA --> TOOLBAR
        TA --> TABS
        TPF --> RECYCLER
        TPF --> JSON
    end
```

Sources: [library/src/main/AndroidManifest.xml:15-25](), [library/src/main/res/layout/chucker_activity_transaction.xml:14-34](), [library/src/main/res/layout/chucker_fragment_transaction_payload.xml:91-116]()

## Core UI Activities

The UI is built around two primary activities defined in the Android manifest, each serving distinct inspection purposes.

```mermaid
graph LR
    subgraph "Activity Definitions"
        MANIFEST["AndroidManifest.xml<br/>(Activity Registration)"]
        
        MAIN_ACT["MainActivity<br/>android:name=...internal.ui.MainActivity"]
        TRANS_ACT["TransactionActivity<br/>android:name=...internal.ui.transaction.TransactionActivity"]
        
        MANIFEST --> MAIN_ACT
        MANIFEST --> TRANS_ACT
    end
    
    subgraph "Activity Properties"
        SINGLE_TASK["android:launchMode=singleTask<br/>(Single Instance)"]
        TASK_AFFINITY["android:taskAffinity=...chucker.task<br/>(Separate Task Stack)"]
        PARENT_ACT["android:parentActivityName=MainActivity<br/>(Navigation Parent)"]
        THEME["android:theme=@style/Chucker.Theme<br/>(Material Design Theme)"]
        
        MAIN_ACT --> SINGLE_TASK
        MAIN_ACT --> TASK_AFFINITY
        TRANS_ACT --> PARENT_ACT
        MAIN_ACT --> THEME
        TRANS_ACT --> THEME
    end
    
    subgraph "Supporting Components"
        SERVICE["ClearDatabaseService<br/>(Background Data Cleanup)"]
        RECEIVER["ClearDatabaseJobIntentServiceReceiver<br/>(Cleanup Events)"]
        PROVIDER["ChuckerFileProvider<br/>(File Sharing Support)"]
        
        MANIFEST --> SERVICE
        MANIFEST --> RECEIVER  
        MANIFEST --> PROVIDER
    end
```

Sources: [library/src/main/AndroidManifest.xml:15-44]()

## Transaction Detail Interface

The `TransactionActivity` provides a tabbed interface for comprehensive transaction inspection using Material Design components.

```mermaid
graph TB
    subgraph "TransactionActivity Layout Structure"
        COORDINATOR["CoordinatorLayout<br/>(Root Container)"]
        APPBAR["AppBarLayout<br/>(Collapsing Header)"]
        TOOLBAR["MaterialToolbar<br/>(Navigation & Actions)"]
        TABLAYOUT["TabLayout<br/>(Overview/Request/Response Tabs)"]
        VIEWPAGER["ViewPager<br/>(Tab Content Container)"]
        
        COORDINATOR --> APPBAR
        APPBAR --> TOOLBAR
        APPBAR --> TABLAYOUT
        COORDINATOR --> VIEWPAGER
    end
    
    subgraph "Tab Content"
        OVERVIEW_TAB["Overview Fragment<br/>(Transaction Summary)"]
        REQUEST_TAB["Request Fragment<br/>(TransactionPayloadFragment)"]
        RESPONSE_TAB["Response Fragment<br/>(TransactionPayloadFragment)"]
        
        VIEWPAGER --> OVERVIEW_TAB
        VIEWPAGER --> REQUEST_TAB
        VIEWPAGER --> RESPONSE_TAB
    end
    
    subgraph "Toolbar Features"
        TITLE["Toolbar Title<br/>(Transaction URL/Method)"]
        MENU["Action Menu<br/>(Share/Export Options)"]
        
        TOOLBAR --> TITLE
        TOOLBAR --> MENU
    end
```

Sources: [library/src/main/res/layout/chucker_activity_transaction.xml:1-44]()

## Payload Display Components

The `TransactionPayloadFragment` handles request and response body display with multiple presentation modes and interactive features.

```mermaid
graph TB
    subgraph "Payload Fragment Layout"
        CONSTRAINT["ConstraintLayout<br/>(Root Container)"]
        PROGRESS["ProgressBar<br/>(Loading State)"]
        EMPTY_GROUP["Group<br/>(Empty State UI)"]
        HIGHLIGHT_PANEL["LinearLayout<br/>(Control Panel)"]
        JSON_VIEW["JsonViewLayout<br/>(JSON Formatting)"]
        RECYCLER["RecyclerView<br/>(Text Display)"]
        FAB["FloatingActionButton<br/>(Scroll Navigation)"]
        
        CONSTRAINT --> PROGRESS
        CONSTRAINT --> EMPTY_GROUP
        CONSTRAINT --> HIGHLIGHT_PANEL
        CONSTRAINT --> JSON_VIEW
        CONSTRAINT --> RECYCLER
        CONSTRAINT --> FAB
    end
    
    subgraph "Empty State Components"
        EMPTY_IMAGE["ImageView<br/>(@drawable/chucker_empty_payload)"]
        EMPTY_TEXT["TextView<br/>(@string/chucker_response_is_empty)"]
        
        EMPTY_GROUP --> EMPTY_IMAGE
        EMPTY_GROUP --> EMPTY_TEXT
    end
    
    subgraph "Control Panel Buttons"
        PLAIN_TOGGLE["MaterialButton<br/>(@string/chucker_highlight)"]
        COLLAPSE_BTN["MaterialButton<br/>(@string/chucker_collapse)"]
        EXPAND_BTN["MaterialButton<br/>(@string/chucker_expand)"]
        
        HIGHLIGHT_PANEL --> PLAIN_TOGGLE
        HIGHLIGHT_PANEL --> COLLAPSE_BTN
        HIGHLIGHT_PANEL --> EXPAND_BTN
    end
    
    subgraph "Display Modes"
        JSON_MODE["JSON View Mode<br/>(Formatted JSON Display)"]
        TEXT_MODE["Text View Mode<br/>(Raw Content Display)"]
        
        JSON_VIEW --> JSON_MODE
        RECYCLER --> TEXT_MODE
    end
```

Sources: [library/src/main/res/layout/chucker_fragment_transaction_payload.xml:1-143]()

## UI Text Resources and Features

The strings resource file defines all user-facing text and reveals the comprehensive feature set available in the interface.

| Feature Category | String Keys | Functionality |
|------------------|-------------|---------------|
| **Core Navigation** | `chucker_name`, `chucker_overview`, `chucker_request`, `chucker_response` | Basic UI labels and navigation |
| **Transaction Data** | `chucker_url`, `chucker_method`, `chucker_protocol`, `chucker_status`, `chucker_ssl` | HTTP transaction field labels |
| **Timing Information** | `chucker_request_time`, `chucker_response_time`, `chucker_duration` | Performance metrics display |
| **Size Information** | `chucker_request_size`, `chucker_response_size`, `chucker_total_size` | Data size metrics |
| **Security Details** | `chucker_tls_version`, `chucker_tls_cipher_suite` | SSL/TLS security information |
| **Export Features** | `chucker_export`, `chucker_share_as_text`, `chucker_share_as_curl`, `chucker_share_as_har` | Multiple export format support |
| **File Operations** | `chucker_save`, `chucker_file_saved`, `chucker_file_not_saved` | File saving capabilities |
| **Search & Display** | `chucker_search`, `chucker_show_plain`, `chucker_highlight`, `chucker_expand`, `chucker_collapse` | Content inspection tools |
| **Data Management** | `chucker_clear`, `chucker_clear_http_confirmation` | Transaction history management |

Sources: [library/src/main/res/values/strings.xml:1-66]()

## Content Display States

The payload fragment implements multiple display states to handle different types of HTTP body content and user interactions.

```mermaid
stateDiagram-v2
    [*] --> Loading
    Loading --> EmptyState: No content
    Loading --> JSONDisplay: Valid JSON content
    Loading --> TextDisplay: Non-JSON content
    
    JSONDisplay --> JSONExpanded: Expand button
    JSONExpanded --> JSONCollapsed: Collapse button
    JSONDisplay --> PlainText: Plain toggle
    PlainText --> JSONDisplay: Highlight toggle
    
    TextDisplay --> TextHighlighted: Highlight toggle
    TextHighlighted --> TextPlain: Plain toggle
    
    EmptyState --> [*]: Content loaded
    JSONDisplay --> [*]: Navigation away
    TextDisplay --> [*]: Navigation away
    
    note right of Loading
        ProgressBar visible
        android:visibility="visible"
    end note
    
    note right of EmptyState  
        EmptyStateGroup visible
        ImageView + TextView
    end note
    
    note right of JSONDisplay
        JsonViewLayout visible
        Control panel buttons shown
    end note
    
    note right of TextDisplay
        RecyclerView visible
        Line-by-line content
    end note
```

Sources: [library/src/main/res/layout/chucker_fragment_transaction_payload.xml:10-125]()

## Notification System Integration

The UI integrates with Android's notification system to provide persistent access to HTTP inspection capabilities.

| String Resource | Purpose | Implementation |
|-----------------|---------|----------------|
| `chucker_http_notification_title` | "Recording HTTP activity" | Persistent notification title |
| `chucker_network_notification_category` | "Chucker network requests" | Notification channel categorization |
| `chucker_shortcut_label` | "Open Chucker" | App shortcut label for quick access |

Sources: [library/src/main/res/values/strings.xml:4](), [library/src/main/res/values/strings.xml:43](), [library/src/main/res/values/strings.xml:60]()

## File Provider Configuration

The UI supports file sharing through a custom `FileProvider` implementation that enables secure sharing of transaction data and exported files.

```mermaid
graph LR
    subgraph "File Provider Setup"
        PROVIDER["ChuckerFileProvider<br/>(android:name)"]
        AUTHORITY["Provider Authority<br/>(applicationId.com.chuckerteam.chucker.provider)"]
        PATHS["File Provider Paths<br/>(@xml/chucker_provider_paths)"]
        PERMISSIONS["Grant URI Permissions<br/>(android:grantUriPermissions=true)"]
        
        PROVIDER --> AUTHORITY
        PROVIDER --> PATHS
        PROVIDER --> PERMISSIONS
    end
    
    subgraph "File Operations"
        SAVE_BODY["Save Body to File<br/>(@string/chucker_save)"]
        SHARE_FILE["Share as File<br/>(@string/chucker_share_as_file)"]
        EXPORT_HAR["Export HAR File<br/>(@string/chucker_share_as_har)"]
        
        PROVIDER --> SAVE_BODY
        PROVIDER --> SHARE_FILE
        PROVIDER --> EXPORT_HAR
    end
    
    subgraph "Intent Queries"
        CREATE_DOCUMENT["CREATE_DOCUMENT Intent<br/>(File Creation Support)"]
        MIME_TYPES["Mime Type Support<br/>(data android:mimeType=*)"]
        
        CREATE_DOCUMENT --> MIME_TYPES
    end
```

Sources: [library/src/main/AndroidManifest.xml:5-44](), [library/src/main/res/values/strings.xml:34](), [library/src/main/res/values/strings.xml:32-33]()
