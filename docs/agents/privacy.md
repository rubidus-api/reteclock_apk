# Privacy And Secrets

Never commit, push, print, log, document, or paste:

- credentials, tokens, API keys, SSH keys, cookies, or `*.pem` files;
- personal information, private accounts, or private email addresses;
- local absolute paths;
- AI server names, build server names, hostnames, IPs, ports, usernames, SSH key names, mount names, share names, private remote URLs, or token filenames.

Keep sensitive files in a dedicated ignored directory rather than relying on
broad filename rules. `.gitignore` ignores these by convention:

```text
*_private/   e.g. app_private/ — local private material kept beside the project
*_internal/
secrets/     keys, tokens, certificates, credentials
.env         (commit a .env.example template instead)
```

This lets the rest of the project stay fully git-tracked with no exposure risk,
without enumerating secret filenames. Do not add broad globs such as `*token*`
or `*.key`; they also hide normal source files (a tokenizer, a Keynote `.key`).

For private knowledge you also want versioned, use a sibling private git
repository under the project parent:

```text
../<project-name>_private/
```

Raw secrets should remain encrypted or untracked even there, unless the user explicitly accepts the risk.

Use project `DECISIONS.md` only for non-secret accepted decisions. Put private decisions, private requirements, incidents, non-public runbooks, and private research notes in the sibling private repository when they need versioned backup.

Before git operations or public output, run `git status`, review `git diff`, and scan for private data.

Apply the same privacy rules to `BACKLOGS.md`, `HANDOFF.md`, detailed backlog items, handoff archives, and migration notes. These files often capture recent work and must not contain secrets, local absolute paths, private host details, token filenames, or private account data.
