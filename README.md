# Text to Rule Converter

Converts natural language telecom rules into structured JSON and logic.

## Workflow Visualization

```mermaid
graph TD
    VAL[Validation Agent] -->|Success| DEC[Decomposition Agent]
    DEC -->|Success| CON_DEC[Consistency Check (Decomposition)]
    CON_DEC -->|Success| SCH[Schedule Extraction]
    SCH --> CON_EXT[Condition Extraction]
    CON_EXT --> CON_CHK[Consistency Check (Condition)]
    CON_CHK -->|Success| RULE[Rule Converter Agent]
    RULE --> UNI[Unified Rule Agent (KPI & IF)]
    UNI --> END((End))
```

## Example Output Tree (Unified Rule Agent)

```mermaid
graph TD
    classDef root fill:#f9f,stroke:#333,stroke-width:2px;
    classDef condition fill:#e1f5fe,stroke:#01579b,stroke-width:2px;
    classDef logic fill:#fff9c4,stroke:#fbc02d,stroke-width:2px;
    classDef schedule fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px;
    classDef action fill:#fff3e0,stroke:#ef6c00,stroke-width:2px;
    classDef policy fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px;
    classDef defaultNode fill:#fff,stroke:#333,stroke-width:1px;
    N1079528191["Run this campaign weekly on Mondays and Tuesdays from 5 October 2024 to 5 October 2026..."]:::root
    N1079528191 --> N283669489
    N283669489["If the subscriber's SMS revenue in the last 30 days is exactly 15 RO..."]:::defaultNode
    N283669489 --> N343069183
    N343069183["For subscribers who are eligible based on having SMS revenue of exactly 15 RO..."]:::defaultNode
    N343069183 --> N822430397
    N822430397["SMS revenue of exactly 15 RO..."]:::defaultNode
    N822430397 --> N1779176412
    N1779176412["if ((Total_Sms_Rev_30D = 15) AND (Total_Recharge_30D >= 200) AND (FAVORITE_LOCATION = 'Mumbai'))"]:::defaultNode
    N822430397 --> N958956183
    N958956183["Send a promotional SMS with Message ID 24"]:::action
    N283669489 --> N995574116
    N995574116["For subscribers who are eligible based on having SMS revenue greater than 15 RO..."]:::defaultNode
    N995574116 --> N538752017
    N538752017["SMS revenue greater than 15 RO..."]:::defaultNode
    N538752017 --> N704958781
    N704958781["if ((Total_Sms_Rev_30D > 15) AND (Recharge_Amount_30D >= 150) AND (MOST_FREQUENT_LOCATION = 'Bengaluru'))"]:::defaultNode
    N538752017 --> N1472578256
    N1472578256["Send a promotional SMS with Message ID 25"]:::action
    N1079528191 --> N1444476205
    N1444476205["Run this campaign weekly on Mondays and Tuesdays..."]:::schedule
    N1444476205 --> N325152707
    N325152707["Extracted Schedule: Valid for weekly execution on Mon, Tue."]:::defaultNode
```
