# Claude Code in this project

The rules for all agents are in AGENTS.md and apply in full: @AGENTS.md

Only what is specific to Claude Code follows.

## The ub-mode skill

- ub-mode (see "Quality gates" in AGENTS.md) is installed as a Claude Code skill: load the `ub-mode` skill before working on a step.
- The skill lives in `.claude/skills/ub-mode/`, which is git-ignored (`/.claude/`). In a fresh clone, copy `SKILL.md` and `LICENSE` from github.com/unclebob/ub-mode (commit 3da62bf, 2026-11-02) into that folder; `SOURCE.md` there records the commit.
- Where the skill differs from AGENTS.md, AGENTS.md wins.
