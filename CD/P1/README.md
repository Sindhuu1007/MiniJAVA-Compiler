# CD P1 — MacroJava Translation

A MacroJava-style translator/preprocessor implemented with **Flex and Bison**.

## Features

- Parses a Java-like subset.
- Supports expression and statement macro definitions.
- Expands macros in expression and statement contexts.
- Handles macro parameter substitution and checks argument-count mismatches.
- Supports lambda-related syntax and function imports in the grammar.
- Emits translated Java-like output.

## Files

- `P1.l` — lexical analysis.
- `P1.y` — grammar and translation actions, including macro expansion.

## Build

The implementation requires Flex and Bison, plus a C++ compiler. Generate lexer/parser sources using the tools and compile the generated files together with the implementation. Exact commands depend on the installed tool versions and file naming conventions.
