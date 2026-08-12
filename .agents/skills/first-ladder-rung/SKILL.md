---
name: first-ladder-rung
description: >-
  Trace the real flow and explicit requirements first. Stop at the first ladder rung
  that fully satisfies them: YAGNI, reuse, standard library, native platform, installed
  dependency, one expression, or minimum implementation.
---

# First Ladder Rung Skill

Use this skill to guide efficient, minimal, and correct implementation by tracing real control flow and stopping at the first abstraction level (ladder rung) that completely satisfies all explicit requirements.

---

## The Implementation Ladder

When addressing coding tasks, trace the exact flow and requirements first, then stop at the earliest applicable rung:

1. **YAGNI (You Aren't Gonna Need It)**: Do not write unnecessary code, speculative features, or unrequested abstraction layers.
2. **Reuse**: Leverage existing helper methods, utility classes, and pre-existing project infrastructure before inventing new ones.
3. **Standard Library**: Prefer built-in language/runtime functions over third-party packages or custom code.
4. **Native Platform**: Use platform-native capabilities and OS APIs when available.
5. **Installed Dependency**: Use dependencies already declared in the project before introducing new external packages.
6. **One Expression**: If logic can be stated clearly and safely in a single expression or function call, prefer it over multi-step boilerplate.
7. **Minimum Implementation**: Write the smallest, cleanest implementation that fully satisfies the contract and requirements.

---

## One-File Skill Boundary & Completion Gate

### MIT Notice Boundary
Ensure any standalone script, component, or file created as part of this skill maintains proper header and license metadata (MIT Notice Boundary).

### Six-Item Completion Gate
Before declaring completion, verify all six items:
1. **Requirements Traced**: Real execution flow and explicit requirements verified against authoritative sources.
2. **First Ladder Rung Selected**: Lowest sufficient abstraction chosen (YAGNI / Reuse / StdLib / Native Platform / Dependency / One Expression / Min Impl).
3. **No Unnecessary Code**: Zero speculative complexity, unused methods, or dummy fallbacks.
4. **Verification Passed**: Clean compile/build and empirical runtime check performed.
5. **License & Headers Intact**: File boundaries and notices preserved.
6. **No Breaking Side Effects**: API contracts and existing caller signatures preserved.

---

## License

MIT License

Copyright (c) 2026

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN CONTRACT/TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
