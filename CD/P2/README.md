# CD P2 — MiniJava Type Checker

This assignment performs semantic type checking on parsed MiniJava programs.

## Pipeline

1. Parse input using `MiniJavaParser`.
2. Build a symbol table using `BuildST`.
3. Check method overriding.
4. Run `TypeChecking` over the program.

## Main files

- `P2.java` — orchestration and entry point.
- `BuildST.java` — symbol-table construction.
- `SymbolTable.java` — program/class/member information and semantic checks.
- `TypeChecking.java` — type-checking logic.
- `syntaxtree/` and `visitor/` — AST and visitor infrastructure.

## Run

Compile the Java files in this assignment with the included parser and AST sources, then run `P2` with a MiniJava program on standard input. The implementation reports parsing/type-checking errors through its error handling.
