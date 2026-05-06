---
name: line-budget-check
description: Audit recently-changed files; flag any single file growing past 300 lines and suggest split points (controller/service/dto/mapper). Run between phases.
---

# Line Budget Check (300-line rule)

When invoked, do the following:

1. List Java source files under `src/main/java` and `src/test/java` with line counts:
   - Run: `find src/main/java src/test/java -name '*.java' -exec wc -l {} + | sort -n`
2. For every file `> 300` lines, report:
   - Path and line count
   - The classes/methods inside (use Read + a quick scan)
   - A concrete split suggestion based on Spring layering:
     - Controller too big → extract a second controller by resource, or push logic into Service
     - Service too big → split by use-case (`EmployeeQueryService` vs `EmployeeCommandService`) or extract a `Mapper`
     - Entity too big → extract `@Embeddable` value objects, move derived getters to a service
     - DTO file too big → split request/response into separate files
     - Test class too big → split by scenario (happy-path vs validation vs error cases)
3. For files between `200` and `300` lines, list them as **warnings** (approaching the budget).
4. Do NOT auto-refactor. Only report. The user decides what to split.

## Why 300?

Hard cap that forces single-responsibility before files become unreviewable. In Spring apps the natural split lines (Controller / Service / Repository / DTO / Mapper / Exception) make 300 a comfortable ceiling — going past it usually means a layer is doing two jobs.

## Output format

```
=== Line Budget Report ===
OVER BUDGET (>300):
  src/main/java/.../EmployeeService.java  412 lines
    Suggested split: extract EmployeeQueryService (read-only @Transactional methods)
                     and EmployeeCommandService (write methods).

WARNINGS (200–300):
  src/main/java/.../EmployeeController.java  247 lines

OK: 12 files under 200 lines.
```
