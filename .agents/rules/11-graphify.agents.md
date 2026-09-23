# Graphify

- A built knowledge graph lives at `graphify-out/` and must be consulted before raw file browsing.
- Always run these commands from the repository root; the manifest stores root-relative paths.
- For a codebase question, run `graphify query "<question>"` first.
- Use `graphify path "<A>" "<B>"` for how two things relate, and `graphify explain "<concept>"` for one concept with its neighbours.
- Use `graphify god-nodes` to find the architectural hubs, and `graphify affected "<X>"` to find what a change touches.
- Read `graphify-out/GRAPH_REPORT.md` only for broad architecture review, or when query, path, and explain do not surface enough context.
- Read raw files when the question is about exact lines, when debugging, and when verifying a graph claim against the code.
- The graph indexes `.java`, `.md`, `.sql`, `.yaml`, and `.xml` files only.
- It does not index `application.properties`, `mvnw`, `.gitignore`, or anything under `.work/`, so read those directly; the spec and task contract are invisible to graphify.
- After modifying code, run `graphify update .`; it is AST-only, needs no API key, and takes about half a minute.
- Never commit `graphify-out/`; it is gitignored regenerable build output that already keeps its own dated backups.
- Community names fall back to placeholders when labelling fails, so check `.graphify_labels.json` for `Community N` after any relabel.
- Relabel with `GRAPHIFY_MAX_OUTPUT_TOKENS=8192 ANTHROPIC_API_KEY="$ANTHROPIC_AUTH_TOKEN" graphify label .`; the default output budget is too small for the local proxy model, which spends it thinking and returns an empty reply.
- `GRAPHIFY_DISABLE_THINKING=1` does not affect the `claude` backend, so it is not a substitute for raising the output budget.
- The `claude` backend reads `ANTHROPIC_BASE_URL` and `ANTHROPIC_API_KEY`, not `ANTHROPIC_AUTH_TOKEN`, so the token must be passed across as the key.
- When the user types `/graphify`, follow the graphify skill rather than this rule.
