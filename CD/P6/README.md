# CD P6 — MiniRA Conversion

This assignment processes **MiniRA** input through a conversion implementation.

## Implementation

- **Parser:** `MiniRAParser` parses input into a syntax tree.
- **Conversion:** the `Conversion` visitor processes the tree.
- **Backend concepts:** the AST and visitor infrastructure includes register, spill, and argument-related structures.
- **Entry point:** `P6.java` invokes the parser and conversion.

## Run

Compile the Java sources in this directory and run `P6` with MiniRA input on standard input. Consult the assignment files for expected output and target details.
