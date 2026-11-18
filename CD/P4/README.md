# CD P4 — MiniIR Conversion

This assignment parses MiniIR and converts it into **MicroIR**.

## Pipeline

1. Parse MiniIR using `MiniIRParser`.
2. Traverse the parsed syntax tree with `Conversion`.
3. Print the converted MicroIR.

## Main files

- `P4.java` — entry point.
- `miniIR.jj` — parser grammar.
- `Conversion.java` — conversion logic.
- `syntaxtree/` and `visitor/` — syntax-tree and visitor infrastructure.

## Run

Compile the Java sources in this folder and run `P4` with MiniIR input on standard input. Converted MicroIR is printed to standard output.
