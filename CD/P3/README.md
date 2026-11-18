# CD P3 — MiniJava to MiniIR

This assignment translates a MiniJava program into **MiniIR**.

## Pipeline

1. Parse the input MiniJava program.
2. Build a symbol table.
3. Compute class layout/offset information.
4. Traverse the AST and generate MiniIR using `MiniIRGen`.

## Main files

- `P3.java` — entry point and orchestration.
- `BuildST.java` / `SymbolTable.java` — symbol-table and layout information.
- `MiniIRGen.java` — MiniIR generation.
- `syntaxtree/` and `visitor/` — AST and traversal infrastructure.

## Run

Compile the Java sources in this folder and run `P3`, supplying a MiniJava program on standard input. Generated MiniIR is written to standard output.
