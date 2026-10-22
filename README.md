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

WhereIsMyMoneyGoing parses Indian bank statements locally on your machine, generates interactive spending dashboards, automatically detects recurring subscriptions, and lets you ask questions about your finances through an AI assistant that understands **English**, **Hinglish**, and **Hindi**, with **no financial data leaving your machine**.

[Quickstart](#-quick-start) • [Features](#-features) • [Trilingual NLP](#-chat-in-english-hinglish--hindi) • [Architecture](#-architecture) • [Benchmarks](#-performance-benchmarks) • [Testing](#-testing)

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

| | Typical Cloud Finance Apps | 💸 WhereIsMyMoneyGoing |
|:---|:---:|:---:|
| **Statement storage** | Stored on third-party cloud DBs | **In-memory per session; nothing written to disk** |
| **Financial data egress** | Uploaded to external SaaS APIs | **None. Statements and chat never leave your machine** |
| **LLM query processing** | Sent to third-party proprietary LLMs | **Local Ollama inference (`qwen2.5:1.5b`)** |
| **Network exposure** | Remote servers | **Docker port bound to `127.0.0.1` only** |
| **Password-protected files** | Decrypted on cloud servers | **Decrypted locally in JVM memory** |
| **Internet dependency** | Always online | **Offline after the first run** (internet is needed once to pull Docker images and the model) |

Only your custom merchant rules are saved to disk (`merchant.local.yml`, kept in a Docker volume).

---

## ⚡ Quick Start

Run the whole stack (Spring Boot app + Ollama + local model) with a **single command**:

```bash
docker compose up
```

> Open **[`http://localhost:8080`](http://localhost:8080)** in your browser. The first start downloads the model (~900 MB) once.

<details>
<summary><b>🔍 What happens during startup?</b></summary>
<br/>

1. **Ollama service** starts in the background.
2. **Model pull worker** checks whether `qwen2.5:1.5b` is cached; if not, it downloads it into a persistent Docker volume (`ollama-models`).
3. **App container** builds and boots the Spring Boot backend on Java 21 (Temurin).
4. Custom merchant rules are preserved in a dedicated Docker volume (`app-data`).
</details>

<details>
<summary><b>⚙️ Advanced Docker profiles (GPU / existing Ollama)</b></summary>
<br/>

- **NVIDIA GPU acceleration:**
```bash
  docker compose -f docker-compose.yml -f docker-compose.gpu.yml up
```

- **Use an Ollama instance already running on your host (port 11434):**
```bash
  docker compose -f docker-compose.host-ollama.yml up
```

- **Use a different model:**
```bash
  OLLAMA_MODEL=llama3.2:3b docker compose up
```
</details>

<details>
<summary><b>💻 Run without Docker</b></summary>
<br/>

1. Install [Ollama](https://ollama.ai) and run `ollama pull qwen2.5:1.5b`.
2. Make sure Java 21 is installed, then start the app:
```bash
   ./mvnw spring-boot:run
```
3. Open `http://localhost:8080`.

The app still starts and the dashboard works if Ollama isn't running; only the AI chat is unavailable.
</details>

---

## ✨ Features

### 🏦 Multi-Bank Statement Ingestion
- Parsers for **HDFC Bank**, **ICICI Bank**, and **State Bank of India (SBI)** `.xlsx` exports.
- Opens **password-protected workbooks** in memory, with no temp files.
- Header normalisation (`ColumnAliases`) maps differing bank formats to one standard schema.
- Pre-loaded **synthetic** sample statements (HDFC and ICICI, including 10,000-transaction versions) on the landing page for quick testing.

> **Note:** The HDFC and ICICI parsers are tested on synthetic statements. The SBI parser is included but has not yet been tested against real SBI exports.

### 🧠 180+ Built-In Indian Merchant Rules
- A rule engine covering major Indian merchants across categories:
  - **Food & Groceries**: Zomato, Swiggy, Blinkit, Zepto, BigBasket, DMart, More, Nature's Basket
  - **Investments & Finance**: Zerodha, Groww, Upstox, Kuvera, BillDesk, NPS, PPF, SSY
  - **Subscriptions & Utilities**: Netflix, Spotify, Prime Video, Hotstar, Jio, Airtel, ACT Fibernet, BESCOM
  - **Travel & Mobility**: Uber, Ola, Rapido, IRCTC, MakeMyTrip, IndiGo
- Token-gluing matches merged narrations such as `ACTFIBERNET` or `SWIGGYUPI`.
- **Developer Mode**: add custom rules from the UI. They are saved to `merchant.local.yml` and applied to your transactions immediately, without a restart.

### 📈 Smart Analytics & Recurring Payment Detection
- **Cash flow**: monthly debits, credits, savings rate, and net change.
- **Balance tracking**: balance trajectory charts rendered client-side with Chart.js.
- **Subscription detection**: finds recurring debits using cadence gaps (21–45 days) and amount stability (median clustering, ±15%).

---

## 🗣️ Chat in English, Hinglish & Hindi

The assistant uses a custom trilingual NLP pipeline. Instead of passing raw text to an LLM, questions go through a deterministic engine first:

> **Questions about your data are answered by Java code that computes exact figures from your transactions.** The LLM is used only for subjective guidance and open-ended advice.

```
                  ┌────────────────────────────────┐
                  │ User Query (EN / Hinglish / HI)│
                  └───────────────┬────────────────┘
                                  │
                      [ LanguageDetector ]
                                  │
                      [ TextNormalizer & Lexicon ]
                                  │
                      [ QueryParser (Intent & Entity) ]
                                  │
                  ┌───────────────┴───────────────┐
                  ▼                               ▼
        [ Data-Backed Intent ]          [ Advice / Open-Ended ]
                  │                               │
          [ QueryExecutor ]              [ AssistantService ]
       Computes exact INR values      Streams a reply from the
       from immutable records         local Ollama LLM, grounded
                  │                   in computed facts
                  │                               │
                  └───────────────┬───────────────┘
                                  ▼
                       Streaming Response
```

### Example Dialogues

| Intent | Query (English / Hinglish / Hindi) | Answered by |
|:---|:---|:---:|
| **Merchant spend** | *"How much did I spend on Zomato this month?"*<br/>*"is mahine zomato pe kitna kharcha hua?"*<br/>*"इस महीने ज़ोमैटो पर कितना खर्च हुआ?"* | **Java engine** (deterministic) |
| **Month-on-month** | *"compare this month with last month"*<br/>*"pichle mahine se compare karo"* | **Java engine** (deterministic) |
| **Category breakdown** | *"how much did I invest in mutual funds?"*<br/>*"nivesh kitna kiya?"* | **Java engine** (deterministic) |
| **Subscriptions** | *"show my active subscriptions"*<br/>*"mere recurring kharche dikhao"* | **Java engine** (deterministic) |
| **Follow-ups** | *User:* "zomato kitna hua?" → *Bot:* "₹1,280 in Oct"<br/>*User:* "aur pichle mahine?" → *Bot:* "₹1,550 in Sept" | **Conversation context** |
| **Savings advice** | *"How can I cut down my lifestyle expenses?"*<br/>*"paisa kaise bachau?"* | **Local LLM** (`qwen2.5:1.5b`) |

---

## 🏛️ Architecture

```
src/main/java/com/whereismymoneygoing/backend/
├── ai/              # Spring AI chat client and prompt-facts builder
│   ├── AssistantService.java         # Streaming orchestrator & Ollama health check
│   └── FinanceFactsBuilder.java      # Injection-safe financial facts for the prompt
├── analytics/       # Calculations & subscription discovery
│   ├── AnalyticsService.java         # Cash flow, coverage, cadence detection
│   └── InrFormatter.java             # Indian number formatting (₹12,34,567)
├── classifier/      # Transaction categorisation heuristics
├── enrichment/      # Applies merchant identity & category to transactions
├── merchant/        # Rule management & token matching
│   ├── MerchantResolver.java         # Compiled token rules with hot-reload
│   └── NarrationNormalizer.java      # Bank narration clean-up
├── model/           # Java 21 immutable records (Transaction, ParsedStatement, ...)
├── nlp/             # Deterministic multilingual intent parsing
│   ├── LanguageDetector.java         # Script & marker-based detection
│   ├── TextNormalizer.java           # Devanagari nukta & diacritic normalisation
│   ├── QueryParser.java              # Entity & time-window extraction
│   └── QueryExecutor.java            # Metric aggregation
├── parser/          # Template Method pattern bank statement parsers
│   ├── AbstractGridParser.java       # Reusable grid scanner & row mapper
│   ├── HdfcExcelParser.java
│   ├── IciciExcelParser.java
│   └── SbiExcelParser.java
└── web/             # Controllers
    ├── StatementController.java      # Dashboard & developer mode routes
    └── AssistantController.java      # Streaming chat endpoints
```

---

## 🚀 Performance Benchmarks

Measured on a Windows laptop with Java 21 using the repo's own benchmark tests (warm-up runs first, then median and p95 over repeated runs). The statements are **synthetic**, generated to look like real HDFC and ICICI exports, and include some deliberately unrecognisable merchants.

| Dataset | Transactions | Parse + enrich (median / p95) | Throughput | Merchant recognition | Analytics (median) |
|:---|---:|:---:|:---:|:---:|:---:|
| ICICI sample | 230 | 16.1 ms / 18.7 ms | ~14k txns/s | **94.8%** (218/230) | 0.15 ms |
| HDFC sample | 230 | 14.9 ms / 16.6 ms | ~15k txns/s | **94.8%** (218/230) | 0.07 ms |
| ICICI large | 10,000 | 492 ms / 625 ms | **~20.3k txns/s** | **90.7%** (9,073/10,000) | 2.41 ms |
| HDFC large | 10,000 | 450 ms / 482 ms | **~22.2k txns/s** | **90.7%** (9,073/10,000) | 1.45 ms |

> *Parse + enrich* covers reading the `.xlsx`, mapping rows, normalising narrations and resolving merchants. *Analytics* covers the summary, top merchants and subscription detection. Results vary by machine.

Reproduce:

```bash
./mvnw test -Dtest=MetricsBenchmarkTest   # 230-transaction samples
./mvnw test -Dtest=LargeBenchmarkTest     # 10,000-transaction samples
```

---

## 🧪 Testing

**54 automated tests across 11 test classes, all passing**, plus an application context-load test and two benchmark runners.

```bash
# Run all tests
./mvnw test
```

| Test class | What it covers |
|:---|:---|
| `BankParserTest`, `StatementServiceTest`, `StatementRowMapperTest` | Bank parsers, bank auto-detection, row mapping |
| `AnalyticsServiceTest` | Summary maths, coverage rates, recurring subscription discovery |
| `QueryUnderstandingTest`, `LanguageAndPeriodTest` | English, Hinglish and Devanagari intent parsing, periods, multi-turn follow-ups |
| `MerchantResolverTest`, `NarrationNormalizerTest`, `MerchantRulePersistenceTest` | Merchant matching, narration clean-up, custom rules persisting to YAML |
| `FinanceFactsBuilderTest` | Prompt facts built safely from transaction data |

---

## 🛠️ Tech Stack

- **Core**: [Spring Boot 4.1.1](https://spring.io/projects/spring-boot) on [Java 21 (Temurin)](https://adoptium.net/)
- **AI & local inference**: [Spring AI 2.0.1](https://spring.io/projects/spring-ai) + [Ollama](https://ollama.ai) (`qwen2.5:1.5b`)
- **Excel processing**: [Apache POI 5.5.1](https://poi.apache.org/)
- **UI**: [Thymeleaf](https://www.thymeleaf.org/) + [Chart.js 4.5.1](https://www.chartjs.org/)
- **Build & containers**: Apache Maven, multi-stage Docker, Docker Compose

---

## 🗺️ Known Limitations

- Supports `.xlsx` exports only (no PDF or CSV yet).
- The SBI parser has not been tested on real SBI statements.
- Benchmarks use synthetic data; real-world recognition rates will vary with the merchants in your statements.
- The small default model (`qwen2.5:1.5b`) is quick but limited; swap in a larger one via `OLLAMA_MODEL` if your hardware allows.

---

## 🤝 Contributing

1. **Add a bank parser**: extend [`AbstractGridParser`](src/main/java/com/whereismymoneygoing/backend/parser/AbstractGridParser.java) and implement `canonical()` header mappings.
2. **Add merchant rules**: add keyword patterns to [`merchants.yml`](src/main/resources/config/merchants.yml).
3. **Extend the vocabulary**: add synonyms in [`Lexicon.java`](src/main/java/com/whereismymoneygoing/backend/nlp/Lexicon.java).

---

## 📜 License

Distributed under the **MIT License**. See [`LICENSE`](LICENSE) for details.
