<div align="center">

# 💸 WhereIsMyMoneyGoing

### *Privacy-First, On-Device Personal Finance Analyser & Trilingual AI Assistant*

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg?style=for-the-badge&logo=openjdk)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-brightgreen.svg?style=for-the-badge&logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring_AI-2.0.1-6db33f.svg?style=for-the-badge&logo=spring)](https://spring.io/projects/spring-ai)
[![Ollama](https://img.shields.io/badge/Ollama-Local_LLM-black.svg?style=for-the-badge&logo=ollama)](https://ollama.ai)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg?style=for-the-badge&logo=docker)](https://www.docker.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

<br/>

**Bank statements carry your most sensitive personal data. They shouldn't live on someone else's server.**

WhereIsMyMoneyGoing parses Indian bank statements locally on your machine, generates interactive spending dashboards, automatically detects recurring subscriptions, and lets you interrogate your finances using an AI assistant that understands **English**, **Hinglish**, and **Hindi** — with **zero data egress**.

[Quickstart](#-quick-start) • [Features](#-features) • [Trilingual NLP](#-chat-in-english-hinglish--hindi) • [Architecture](#-architecture) • [Benchmarks](#-performance-benchmarks) • [Test Suite](#-testing)

---

</div>

<br/>

## 📽️ Demo Video & Preview

<div align="center">

<video src="https://github.com/user-attachments/assets/312d2a8f-f57e-456a-b8ed-04df74eb00a2" controls="controls" width="100%" style="max-height: 520px; border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,0.15);">
  <a href="https://github.com/user-attachments/assets/312d2a8f-f57e-456a-b8ed-04df74eb00a2">Watch Demo Video</a>
</video>

<br/>

*Interactive Dashboard · Instant Spend Analytics · Multilingual On-Device LLM Chat*

</div>

---

## 🔒 Why On-Device & Privacy-First?

Traditional expense trackers require uploading raw PDF/Excel statements to third-party cloud servers or connecting aggregator APIs.

| Feature | Typical Cloud Finance Apps | 💸 WhereIsMyMoneyGoing |
|:---|:---:|:---:|
| **Statement Storage** | Stored on third-party cloud DBs | **100% In-Memory / Local Disk only** |
| **Financial Data Egress** | Uploaded to external SaaS APIs | **Zero Network Egress (Localhost only)** |
| **LLM Query Processing** | Sent to third-party proprietary LLMs | **Local Ollama inference (`qwen2.5:1.5b`)** |
| **Security Surface** | Exposed to remote breaches | **Bound to `127.0.0.1` locally** |
| **Password-Protected Files**| Decrypted on cloud servers | **Decrypted locally in JVM memory** |
| **Internet Dependency** | Requires active connection | **Works completely offline** |

---

## ⚡ Quick Start

Run the entire ecosystem (Spring Boot Web App + Ollama Engine + Local AI Model) with a **single command**:

```bash
docker compose up
```

> **That's it!** Open **[`http://localhost:8080`](http://localhost:8080)** in your browser.

<details>
<summary><b>🔍 What happens under the hood during startup?</b></summary>
<br/>

1. **Ollama Service** starts up in the background and initializes local inference bindings.
2. **Model Pull Worker** checks if `qwen2.5:1.5b` (~900MB) is cached; if not, downloads it once into a persistent Docker volume (`ollama-models`).
3. **Application Container** compiles and boots the Spring Boot backend on Java 21 Temurin.
4. Custom user merchant rules are preserved in a dedicated Docker volume (`app-data`).
</details>

<details>
<summary><b>⚙️ Advanced Docker Profiles (GPU / Existing Ollama)</b></summary>
<br/>

- **NVIDIA GPU Acceleration** (Faster LLM responses):
  ```bash
  docker compose -f docker-compose.yml -f docker-compose.gpu.yml up
  ```

- **Use Existing Host Ollama Instance** (If you already run Ollama on port 11434):
  ```bash
  docker compose -f docker-compose.host-ollama.yml up
  ```

- **Switch LLM Model Target**:
  ```bash
  OLLAMA_MODEL=llama3.2:3b docker compose up
  ```
</details>

---

## ✨ Features

### 🏦 Multi-Bank Statement Ingestion
- Native support for **HDFC Bank**, **ICICI Bank**, and **State Bank of India (SBI)** `.xlsx` exports.
- Seamlessly handles **password-encrypted workbooks** without temp files.
- Resilient header normalization (`ColumnAliases`) mapping dynamic bank statement formats to a standardized schema.
- Instant pre-loaded sample datasets available on the landing page for quick testing.

### 🧠 150+ Built-In Indian Merchant Knowledge Base
- Pre-configured rule engine covering major Indian merchants across categories:
  - **Food & Groceries**: Zomato, Swiggy, Blinkit, Zepto, BigBasket, DMart, More, Nature's Basket.
  - **Investments & Finance**: Zerodha, Groww, Upstox, Kuvera, BillDesk, NPS, PPF, SSY.
  - **Subscriptions & Utilities**: Netflix, Spotify, Prime Video, Hotstar, Jio, Airtel, ACT Fibernet, BESCOM.
  - **Travel & Mobility**: Uber, Ola, Rapido, IRCTC, MakeMyTrip, IndiGo.
- Token-gluing engine accurately identifies merged narrations like `ACTFIBERNET` or `SWIGGYUPI`.
- **Developer Mode**: Add custom rules live in the UI; updates write to `merchant.local.yml` and hot-reload transactions in real time without restart.

### 📈 Smart Analytics & Recurring Payment Detection
- **Cash Flow Trajectory**: Monthly debits, credits, savings rate, and net delta.
- **Balance Tracking**: Dynamic balance trajectory charts rendered using client-side Chart.js.
- **Algorithmic Subscription Detection**: Automatically identifies recurring debits using cadence gap constraints (21–45 day cycles) and amount-stability filtering (median clustering ±15%).

---

## 🗣️ Chat in English, Hinglish & Hindi

WhereIsMyMoneyGoing features a custom trilingual NLP pipeline. Rather than dumping raw text into an LLM and hoping for the best, queries pass through a deterministic execution engine:

> **Data questions are answered by Java with 0% hallucination risk.** The LLM is only engaged for subjective financial guidance or open-ended advice.

```
                  ┌───────────────────────────────┐
                  │ User Query (EN / Hinglish / HI)│
                  └──────────────┬────────────────┘
                                 │
                     [ LanguageDetector ]
                                 │
                     [ TextNormalizer & Lexicon ]
                                 │
                     [ QueryParser (Intent & Entity) ]
                                 │
                  ┌──────────────┴──────────────┐
                  ▼                             ▼
       [ Data-Backed Intent ]         [ Advice / Open-Ended ]
                  │                             │
         [ QueryExecutor ]            [ AssistantService ]
      Computes exact INR values       Streams reasoning via
      from immutable records          Local Ollama LLM
                  │                             │
                  └──────────────┬──────────────┘
                                 ▼
                    Streaming Markdown Response
```

### Supported Dialogue Examples

| Intent | Query (English / Hinglish / Hindi) | Response Source |
|:---|:---|:---:|
| **Merchant Spend** | *"How much did I spend on Zomato this month?"*<br/>*"is mahine zomato pe kitna kharcha hua?"*<br/>*"इस महीने ज़ोमैटो पर कितना खर्च हुआ?"* | **Java Engine** (Deterministic) |
| **Month-on-Month** | *"compare this month with last month"*<br/>*"pichle mahine se compare karo"* | **Java Engine** (Deterministic) |
| **Category Breakdown** | *"how much did I invest in mutual funds?"*<br/>*"nivesh kitna kiya?"* | **Java Engine** (Deterministic) |
| **Subscriptions** | *"show my active subscriptions"*<br/>*"mere recurring kharche dikhao"* | **Java Engine** (Deterministic) |
| **Contextual Follow-ups**| *User:* *"zomato kitna hua?"* → *Bot:* *"₹1,280 in Oct"*<br/>*User:* *"aur pichle mahine?"* → *Bot:* *"₹1,550 in Sept"* | **Stateful Memory** |
| **Savings Advice** | *"How can I cut down my lifestyle expenses?"*<br/>*"paisa kaise bachau?"* | **Local LLM** (`qwen2.5:1.5b`) |

---

## 🏛️ Architecture & Clean Design

Built on clean, decoupled architecture leveraging modern Java 21 paradigms:

```
src/main/java/com/whereismymoneygoing/backend/
├── ai/              # Spring AI chat client, memory advisor & RAG context builder
│   ├── AssistantService.java         # Streaming response orchestrator & health probe
│   └── FinanceFactsBuilder.java      # Injection-safe financial facts compiler
├── analytics/       # Statistical calculations & subscription discovery algorithms
│   ├── AnalyticsService.java         # Metric engine (cash flow, coverage, cadence)
│   └── InrFormatter.java             # Indian numbering formatting (₹12,34,567)
├── classifier/      # Transaction categorization heuristics
├── enrichment/      # Pipeline applying merchant identities & categories
├── merchant/        # Rule management & token matching engine
│   ├── MerchantResolver.java         # Compiled token rules with hot-reload
│   └── NarrationNormalizer.java      # Bank text sanitation
├── model/           # Java 21 immutable records (Transaction, ParsedStatement...)
├── nlp/             # Deterministic multilingual intent parsing
│   ├── LanguageDetector.java         # Script & marker-based classifier
│   ├── TextNormalizer.java           # Devanagari nukta & diacritic normalizer
│   ├── QueryParser.java              # Entity & temporal window extractor
│   └── QueryExecutor.java            # Metric aggregation engine
├── parser/          # Template Method pattern bank statement extractors
│   ├── AbstractGridParser.java       # Reusable grid scanner & row mapper
│   ├── HdfcExcelParser.java          # HDFC sheet reader
│   ├── IciciExcelParser.java         # ICICI sheet reader
│   └── SbiExcelParser.java           # SBI sheet reader
└── web/             # Web controllers
    ├── StatementController.java      # Dashboard & developer mode routes
    └── AssistantController.java      # Reactive streaming REST controller
```

---

## 🚀 Performance Benchmarks

Measured using JMH-style warmup passes over 200 iterations on standard consumer hardware:

```
========================= BENCHMARK METRICS =========================
HDFC Bank  :  120 txns  | Parse + Enrich: 7.2 ms  | Analytics: 0.8 ms | Recognition: 82.4%
ICICI Bank :   95 txns  | Parse + Enrich: 5.6 ms  | Analytics: 0.6 ms | Recognition: 79.1%
SBI Bank   :  110 txns  | Parse + Enrich: 6.8 ms  | Analytics: 0.7 ms | Recognition: 80.3%
=====================================================================
```

> **Result:** End-to-end parsing, categorization, and full analytical breakdown executes in **under 10 milliseconds** for standard quarterly statements.

---

## 🧪 Testing

The codebase includes comprehensive unit and integration tests across domain logic, parsers, and language understanding:

```bash
# Run all tests
./mvnw test

# Run the benchmark measurement suite
./mvnw test -Dtest=MetricsBenchmarkTest
```

### Test Coverage Highlights
- ✅ **`BankParserTest`**: Verified against real-world and synthetic statements from HDFC, ICICI, and SBI.
- ✅ **`AnalyticsServiceTest`**: Validates summary maths, coverage rates, and recurring subscription discovery algorithms.
- ✅ **`QueryUnderstandingTest`**: Full suite validating English, Hinglish, and Devanagari Hindi intent parsing and multi-turn conversational follow-ups.
- ✅ **`MerchantRulePersistenceTest`**: Ensures custom runtime merchant rules persist cleanly across YAML updates.

---

## 🛠️ Tech Stack

- **Core Framework**: [Spring Boot 4.1.1](https://spring.io/projects/spring-boot) on [Java 21 (Temurin)](https://adoptium.net/)
- **AI & Local Inference**: [Spring AI 2.0.1](https://spring.io/projects/spring-ai) & [Ollama](https://ollama.ai) (`qwen2.5:1.5b`)
- **Document Processing**: [Apache POI 5.5.1](https://poi.apache.org/) (Streaming OOXML parser)
- **UI & Presentation**: [Thymeleaf](https://www.thymeleaf.org/) & [Chart.js 4.5.1](https://www.chartjs.org/)
- **Build & Containerization**: Apache Maven, Multi-stage Docker, Docker Compose

---

## 🤝 Contributing

Contributions are welcomed!
1. **Add a new bank parser**: Extend [`AbstractGridParser`](src/main/java/com/whereismymoneygoing/backend/parser/AbstractGridParser.java) and implement `canonical()` header mappings.
2. **Add merchant rules**: Add keyword patterns to [`merchants.yml`](src/main/resources/config/merchants.yml).
3. **Enhance vocabulary**: Expand synonyms in [`Lexicon.java`](src/main/java/com/whereismymoneygoing/backend/nlp/Lexicon.java).

---

## 📜 License

Distributed under the **MIT License**. See `LICENSE` for details.
