# MiniJava Compiler

A collection of compiler-design implementations developed across the **Compiler Design** coursework. The repository contains two tracks, **ACD** and **CD**, exploring source-language parsing and analysis, intermediate-code generation, and backend processing.

## Tracks

- **[ACD](./ACD/)** — four assignment folders.
- **[CD](./CD/)** — six assignment folders.

## Compiler Pipeline

```text
Source Program -> Parsing -> AST / Semantic Analysis
              -> Intermediate Representation
              -> IR Processing / Backend
```

The precise stages and input/output formats are assignment-specific.

## ACD Track

| Assignment | Focus |
|---|---|
| [P1](./ACD/P1/) | Program information and symbol-table construction, followed by visitor-based variable analysis. |
| [P2](./ACD/P2/) | Source-program translation using class/member information and visitor-based processing. |
| [P3](./ACD/P3/) | MiniJava/TACoJava-family language processing and translation. |
| [P4](./ACD/P4/) | Processing and transformation of an extended Java-like representation. |

## CD Track

| Assignment | Focus |
|---|---|
| [P1](./CD/P1/) | MacroJava translation/preprocessing using Flex and Bison, including expression and statement macros. |
| [P2](./CD/P2/) | MiniJava parsing, symbol-table construction, and semantic type checking. |
| [P3](./CD/P3/) | MiniJava-to-MiniIR translation. |
| [P4](./CD/P4/) | MiniIR processing and conversion. |
| [P5](./CD/P5/) | MicroIR processing with control-flow and liveness-related analysis utilities. |
| [P6](./CD/P6/) | MiniRA processing and conversion, including register/spill-related support. |

## Layout

```
MiniJAVA-Compiler/
├── ACD/ (P1–P4)
└── CD/  (P1–P6)
```

Assignments may contain parser files, grammar specifications, AST nodes, visitors, checklists, and test programs.

## Getting Started

```bash
git clone https://github.com/Sindhuu1007/MiniJAVA-Compiler.git
cd MiniJAVA-Compiler
```

Navigate to the assignment folder of interest. Build and execution instructions vary by assignment; consult its grammar, source, and configuration files.

## Technologies

- **Java** — parsing infrastructure, ASTs, visitors, analysis, and translation.
- **Flex / Lex** and **Bison / Yacc** — used in CD P1.
- **Generated parser and AST/visitor infrastructure** — used in multiple Java assignments.

## Notes

- This repository contains independent coursework assignments, not a single unified compiler executable.
- Some generated parser and AST files are included in the repository.
