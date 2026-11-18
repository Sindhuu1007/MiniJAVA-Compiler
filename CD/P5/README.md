# CD P5 — MicroIR Analysis and Processing

This assignment processes MicroIR and performs control-flow and data-flow analyses used in later compiler stages.

## Analysis pipeline

The entry point `P5.java` parses MicroIR and coordinates analysis components including:

- Procedure information collection (`Storing`).
- Control-flow successor computation (`Succ`).
- IN/OUT data-flow computation (`InOut`).
- Live-range computation (`LiveRanges`).
- Additional processing/printing and supporting utilities.

## Main files

- `P5.java` — orchestration.
- `microIR.jj` and generated parser sources — input parsing.
- `visitor/` — analysis and processing implementation.

## Run

Compile the Java sources in this directory and run `P5` with MicroIR on standard input. The precise output format is defined by the implementation and assignment requirements.
