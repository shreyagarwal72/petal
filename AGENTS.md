# Agent Guidelines & Rules

## 1. File Reading & Exploration Efficiency
- **NEVER** re-read or inspect the same file multiple times in a conversation turn or workflow.
- Execute direct, targeted edits without redundant views or searches.

## 2. Dual Repository Synchronization
- Always keep `/data/data/com.termux/files/home/petal` and `/data/data/com.termux/files/home/petal_browser` in sync.
- Both repositories must have identical code changes and be pushed to `origin/main`.
