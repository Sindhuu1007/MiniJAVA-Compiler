# ACD P1 — Variable Analysis

This assignment parses a MiniJava-like program, builds program metadata, and performs variable analysis.

## Implementation

- **Parser and AST:** generated parser and syntax-tree infrastructure under this directory.
- **Symbol-table construction:** `SymbolTableBuilder` collects class and method information into program metadata.
- **Analysis:** `AnalysisVisitor` traverses the syntax tree and delegates expression checking to an expression checker.
- **Entry point:** `P1.java` reads a program from standard input, builds metadata, runs analysis, and reports the result or an issue.

## Run

The Java sources are included. Compile/run from this directory using the Java build setup appropriate for the included packages and parser sources. The entry point is `P1`; input is read from standard input.
