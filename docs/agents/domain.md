# Domain Docs

The engineering skills use one shared domain context for this repository.

## Before exploring

- Read GLOSSARY.md at the repository root.
- Read ADRs in docs/adr/ that affect the area under discussion.
- If one of these files does not exist, continue without creating it merely for setup.

## Layout

- GLOSSARY.md defines terms used across the project.
- docs/adr/ records architectural decisions for the project.
- This repository does not use a GLOSSARY-MAP.md or per-package glossaries.

## Consumer rules

- Use the names and distinctions in GLOSSARY.md in issues, specs, tests and implementation plans.
- Avoid synonyms the glossary explicitly rejects.
- If a needed term is absent, note the gap for domain-modeling rather than inventing a competing definition.
- If a proposed change contradicts an ADR, call out that conflict explicitly and explain why it should be reopened.
