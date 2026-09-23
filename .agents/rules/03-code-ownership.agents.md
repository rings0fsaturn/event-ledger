# Code Ownership

- The author writes application code and configuration by default.
- This includes Compose, Dockerfiles, Kubernetes, Terraform, `.env`, CI, Spring config, and similar artifacts.
- If the author explicitly asks the agent to write code, that permission applies only to the requested piece.
- The agent may provide pseudocode, structure, examples, commands, reviews, and explanations.
- The agent may perform mechanical repo setup, git operations, and environment commands.
- The agent may run or inspect code/config while investigating, but does not author the final application fix.
- The goal is that the author understands and owns the resulting implementation.
