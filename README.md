# Java Explainer — Prompt Evaluation Suite

A Spring AI application that evaluates how well Claude explains Java functions
in plain English. Scores each explanation on four dimensions and produces a
formatted Excel report.

---

## What it does

1. **Generates** a dataset of Java functions (simple, medium, complex) using Claude
2. **Runs** an explainer prompt on each function — asking Claude to explain it in plain English
3. **Grades** each explanation using Claude as a judge (LLM-as-judge pattern)
4. **Reports** results to console and saves a formatted Excel file

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 17+ |
| Maven | 3.8+ |
| Anthropic API key | Any active key |

---

## Setup

**Step 1 — Set your API key as an environment variable**

Mac / Linux:
```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

Windows CMD:
```cmd
set ANTHROPIC_API_KEY=sk-ant-...
```

Windows PowerShell:
```powershell
$env:ANTHROPIC_API_KEY="sk-ant-..."
```

> The key is read from the environment via `${ANTHROPIC_API_KEY}` in
> `application.yml`. It is never stored in any file.

**Step 2 — Run**

```bash
mvn spring-boot:run
```

---

## Configuration

All settings are in `src/main/resources/application.yml`:

```yaml
spring:
  main:
    web-application-type: none    # CLI app, no web server needed
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-haiku-4-5-20251001
          max-tokens: 4096          # keep high — complex functions need room

eval:
  dataset-size: 5          # number of Java functions to generate
  pass-threshold: 4.0      # score >= this = PASS (scale of 1-5)
  output-file: eval-report.json
```

---

## Output

After a successful run you get two files in the project root:

### 1. `eval-report.json`
Raw JSON with all results. Useful for further processing or diffing runs.

### 2. `eval-report.xlsx`
Formatted Excel workbook with two sheets:

**Sheet 1 — Summary**

| Section | What it shows |
|---|---|
| Run Details | Date, model, number of functions evaluated |
| Results | Pass count, fail count, pass rate % |
| Average Scores | Score per dimension out of 5 |
| Action | Weakest dimension highlighted in red |

**Sheet 2 — Detail**

One row per function. Columns:

| Column | Content | Background |
|---|---|---|
| A | Function name | — |
| B | Difficulty | 🟢 simple / 🟡 medium / 🔴 complex |
| C | **Full prompt sent to LLM** (system message + Java code) | Blue |
| D | **LLM response** (the explanation that was graded) | Amber |
| E | Ideal explanation (reference answer) | Green |
| F | Accuracy score (1-5) | 🟢 ≥4 / 🟡 =3 / 🔴 ≤2 |
| G | Accuracy reason | — |
| H | Simplicity score | colour coded |
| I | Simplicity reason | — |
| J | Completeness score | colour coded |
| K | Completeness reason | — |
| L | Conciseness score | colour coded |
| M | Conciseness reason | — |
| N | Overall score | colour coded |
| O | PASS / FAIL | 🟢 / 🔴 |

All columns have auto-filter enabled — you can sort by score, filter by
difficulty, or filter to show only failures.

---

## Grading dimensions

Each explanation is scored 1-5 on four dimensions independently:

| Dimension | What is measured | Score 5 | Score 1 |
|---|---|---|---|
| **Accuracy** | Does it correctly describe what the code does? | No mistakes | Wrong or misleading |
| **Simplicity** | Is it free of jargon? | Zero technical terms | Heavy jargon |
| **Completeness** | Does it cover all key parts? | All inputs, outputs, purpose covered | Vague or partial |
| **Conciseness** | Is it tight and direct? | 1-3 sentences | 7+ sentences or padded |

Overall score = average of the four dimensions.
Pass threshold = 4.0 (configurable in `application.yml`).

---

## Project structure

```
src/main/java/com/eval/
│
├── EvalApplication.java              Spring Boot entry point
│
├── model/
│   ├── JavaFunction.java             One dataset item (code + difficulty + ideal explanation)
│   ├── ExplanationResult.java        Output from the explainer prompt
│   ├── GradeResult.java              Four dimension scores + reasons + pass/fail
│   └── EvalReport.java               Aggregated report with averages and weakest dimension
│
├── service/
│   ├── DatasetGenerator.java         Calls Claude to generate Java functions + ideal answers
│   ├── ExplainerService.java         THE PROMPT UNDER TEST — edit this to iterate
│   ├── GraderService.java            LLM-as-judge — scores each explanation
│   ├── ReportService.java            Aggregates results, prints console summary
│   └── ExcelReportService.java       Generates the formatted .xlsx report
│
└── runner/
    └── EvalRunner.java               Orchestrates the full pipeline (Steps 1-3)

src/main/resources/
    application.yml                   All configuration in one place

src/test/java/com/eval/
    GraderParsingTest.java            Unit tests for grader JSON parsing (no API key needed)
```

---

## Iterating on the prompt

The prompt under test lives entirely in `ExplainerService.java`.

Two versions are provided:

- **V1** (active) — basic instruction, establishes a baseline
- **V2** (commented out) — stricter rules, forbidden word list, examples

**To compare V1 vs V2:**
1. Run as-is → note `avgSimplicity`, `avgConciseness`, pass rate from console output
2. Open `ExplainerService.java`, comment out V1, uncomment V2
3. Re-run → compare the new scores
4. The diff tells you exactly what the improved prompt buys you

The grader and dataset stay the same between runs so the comparison is fair.

---

## How the pipeline works

```
mvn spring-boot:run
        │
        ▼
Spring Boot starts — reads application.yml
        │
        ▼
Builds AnthropicChatModel (API key + model config)
        │
        ▼
Injects all services → EvalRunner.run() is called
        │
        ├── Step 1  DatasetGenerator  →  1 API call   →  List<JavaFunction>
        │
        ├── Step 2  ExplainerService  →  N API calls  →  List<ExplanationResult>
        │           (one call per function)
        │
        ├── Step 3  GraderService     →  N API calls  →  List<GradeResult>
        │           (one call per function)
        │
        └── Step 4  ReportService     →  0 API calls  →  console + JSON + Excel
```

**Total API calls per run** = 1 + (2 × dataset-size)

With `dataset-size: 5` that is 11 calls — roughly **$0.001** at Haiku pricing.

---

## Common errors

| Error | Cause | Fix |
|---|---|---|
| `API key must not be null` | `ANTHROPIC_API_KEY` env var not set | Run `export ANTHROPIC_API_KEY=sk-ant-...` |
| `Unexpected end-of-input` | Response truncated — token limit too low | Increase `max-tokens` in `application.yml` |
| `ReactiveWebServerFactory not found` | Spring AI pulls in reactive web | Ensure `web-application-type: none` is in `application.yml` |
| `Could not find artifact spring-ai-anthropic` | Wrong version or missing repo | Ensure `spring-ai.version=1.0.0-M6` and milestone repo is in `pom.xml` |

---

## Running tests (no API key needed)

```bash
mvn test
```

`GraderParsingTest` validates the JSON parsing logic in isolation — no API
calls are made.
