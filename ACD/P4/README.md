# ACD P4

This assignment processes an extended Java-like representation and renders transformed source output.

## Implementation

- **Parser:** `FunkyTacoJavaParser` parses input into a syntax tree.
- **Program model:** `ProgramModel.Builder` collects information and `finalizeModel()` completes the model.
- **Rendering:** `InliningPrinter` renders the transformed program.
- **Entry point:** `P4.java` orchestrates parsing, model construction, and output.

## Run

Compile the Java sources under `llm/` together and run the `P4` entry point. Input is read from standard input; output is printed to standard output.
