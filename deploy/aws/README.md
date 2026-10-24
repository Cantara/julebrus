# Julebrus: AWS deployment

One small EC2 box with nginx (HTTPS by certbot) in front of the Spring Boot jar, RDS PostgreSQL, an S3 artifacts bucket
for releases. The season runs 1 Nov to 23 Dec 2026. Whoever runs `aws` against the account is an operator; every
command below is run by a person (mutating scripts ask for a typed word first). Nothing in this folder has been run
against AWS yet, see "Not verified". In every command `<aws-profile>` is the AWS CLI profile of the sponsoring account;
the scripts have no default and refuse to run without `PROFILE` (or `AWS_PROFILE`).

## Decisions

| Topic | Decision |
|---|---|
| Account, region | A sponsored AWS account, `eu-north-1` |
| Stack | CloudFormation stack `julebrus`; every resource is named and tagged `julebrus-*` / `Project=julebrus` |
| Host name | `xmas.cantara.no`. DNS is not in the stack: the A record is made by hand to the stack's Elastic IP |
| Access to the box | SSM Session Manager only. No key pair, no inbound SSH; only 80 and 443 inbound |
| HTTPS | nginx + certbot on the box (HSTS and `server_tokens off` added by the `tls` step); the jar listens on 127.0.0.1:8080 only |
| Database | RDS PostgreSQL 16, db.t4g.micro, single-AZ, encrypted, not publicly accessible (security group admits the app box only), in the default VPC's subnets, 14-day backups, final snapshot, deletion protection (on after the first successful release, see runbook). The master password is generated once into the secret `julebrus/db` and not rotated |
| Storage | The app stores only database rows, so there is no documents bucket |
| Who may sign in | Open by default: any Google account (`JULEBRUS_ALLOWED_DOMAINS` empty), because the tasting is for the wider Cantara community. Decide explicitly before go-live (runbook step 8) |
| Releases | Pull. A tag `vX.Y.Z` runs `.github/workflows/release.yml`; its upload job uploads `releases/<v>/app.jar` and `latest.json` (with sha256) to `julebrus-artifacts-<account>` through GitHub OIDC. A systemd timer on the box checks every 5 minutes, verifies the sha256, swaps, health-checks `/actuator/health` and rolls back on failure |
| OIDC trust | Role `julebrus-release-uploader` is assumed only by the workflow's upload job running in the GitHub Environment `production` (subject `repo:Cantara/julebrus:environment:production`); it can only write `releases/*` and `latest.json` |

## What the app expects and how this stack provides it

| App setting | Provided by |
|---|---|
| `JULEBRUS_DB_URL` | `/etc/julebrus/app.env` (non-secret, written from the stack). It uses `sslmode=require`: the connection is encrypted but the server certificate is not verified (`verify-full` would need the RDS CA bundle on the box; acceptable here because the database is private and only reachable from the app's security group) |
| `JULEBRUS_DB_USER`, `JULEBRUS_DB_PASSWORD` | fetched at every start from the secret `julebrus/db` into `/run/julebrus/secrets.env` (tmpfs, root-only 0600) by `julebrus-prestart.sh`. The app uses the database master user; a least-privilege application role is a later hardening step |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | same prestart, from the secret `julebrus/google-oauth` `{client-id, client-secret}` that `scripts/google-oauth.sh create` stores |
| `JULEBRUS_ALLOWED_DOMAINS` (empty = any Google account), `JULEBRUS_ADMINS`, `JULEBRUS_CONTACT_EMAIL` | `/etc/julebrus/app.env`, empty at first; changed with `julebrus.sh env set KEY=VALUE` (allow-list of exactly these three keys, the file is never printed) |
| `SERVER_FORWARD_HEADERS_STRATEGY=framework` | `/etc/julebrus/app.env` (see below) |

Why a stable database secret: the RDS-managed master secret rotates every 7 days, but the app reads its password only at
start, so it would lose the database about a week after each start. `julebrus/db` is generated once by CloudFormation
(32 alphanumeric characters, no rotation) and resolved into the instance with a dynamic reference, so the password is not in
the template. An unchanged secret never changes the database, and a later update does not replace it. The secret is retained if the stack is deleted.

Notes on the app as read from the repository (branch point of this work):

- `application.yml` has no `server.forward-headers-strategy`, and `SecurityConfig` does not handle forwarded headers. Behind
  nginx Spring would then build `http://` redirect URIs and Google login would fail. Because `backend/` is not changed
  here, the stack sets the Spring relaxed-binding variable `SERVER_FORWARD_HEADERS_STRATEGY=framework` in `app.env`, and
  nginx sends `X-Forwarded-Proto`, `X-Forwarded-Host` and `X-Forwarded-For`. If the app later sets the property itself, the variable can stay.
- The app reads no sponsor settings. `JULEBRUS_CONTACT_EMAIL` is not read by the app at the time of writing either; it is in the allow-list and in `app.env` so it works the day the app starts reading it, and is harmless until then.
- The pom carries `0.1.0-SNAPSHOT`. The release workflow sets the version from the tag on the build checkout only (`versions-maven-plugin` 2.18.0 `set`), so the jar is `julebrus-<tag version>.jar`.
- The Google OAuth redirect URI to register is `https://xmas.cantara.no/login/oauth2/code/google`.

## Layout

```
DNS A record  ->  Elastic IP  ->  EC2 t4g.small (AL2023 arm64, Java 21; image id fixed at create time)
                                    nginx :80/:443 (certbot) -> Spring Boot 127.0.0.1:8080
                                    julebrus-update.timer pulls releases from S3 every 5 min
RDS PostgreSQL 16 (db.t4g.micro, single-AZ, encrypted, private)
S3  julebrus-artifacts-<account>   releases/<v>/app.jar, latest.json, deploy/ (scripts and units); written by the OIDC role / the operator
Secrets Manager  julebrus/db (generated by the stack), julebrus/google-oauth (made by google-oauth.sh)
SNS + CloudWatch alarms            instance status check, RDS free storage, RDS CPU
```

Files: `template.yaml` (the stack), `scripts/00-provision.sh` (stack state, preflight, change set), `scripts/set-github-variables.sh`,
`scripts/google-oauth.sh`, `scripts/julebrus.sh` (operator, via SSM), and for the box `scripts/box-setup.sh`,
`julebrus-prestart.sh`, `julebrus-update.sh`, `julebrus-env.sh`, `systemd/julebrus.service`, `julebrus-update.service|timer`.
The scripts are not on PATH; usage is in each header. On the box everything under `/opt/julebrus` and `/etc/julebrus` is
root-owned; the service user only reads the jar.

## What the project owner must provide

- An e-mail address for the alarms (CloudWatch -> SNS).
- A Google OAuth client (type: web application) in a Google Cloud project, with the authorised redirect URI
  `https://xmas.cantara.no/login/oauth2/code/google`, and its client id and secret (typed into `google-oauth.sh create`).
- The DNS A record for `xmas.cantara.no` (Cantara's DNS) to the Elastic IP.
- The decision on who may sign in, and the admin e-mails for `JULEBRUS_ADMINS`.
- Admin rights on the GitHub repository `Cantara/julebrus` (Environment, variables, tag ruleset).

## Runbook (an operator with admin access to the account, in order)

Run from `deploy/aws/` of a checkout. `export PROFILE=<aws-profile>` once.

1. **Provision.** `bash scripts/00-provision.sh <alarm-email> [true|false]`. It reads the stack state first and explains what to do for `ROLLBACK_COMPLETE` (offers a typed `DELETESTACK`), `REVIEW_IN_PROGRESS`, `CREATE_FAILED`, `ROLLBACK_FAILED`, `DELETE_FAILED` and any `*_IN_PROGRESS`. Then a read-only preflight (our names are free, the GitHub OIDC provider exists, default VPC and a subnet in a zone that offers the instance type, the Elastic IP quota), a change set that lists exactly what will be created, and a typed `DEPLOY`. Takes about 10 minutes (RDS). The second argument, `UseElasticIp`, counts for this first create only; pass `false` if the allocation is refused (the instance's own IP changes when it is stopped and started). The first create has database deletion protection OFF, so that a failed create can roll back cleanly. The AMI id is resolved now and fixed in the stack.
   If the GitHub organisation uses the immutable OIDC subject format, set `GITHUB_SUBJECT_PREFIX` (see "GitHub setup").
2. **GitHub setup.** Create the Environment and ruleset described under "GitHub setup", then `bash scripts/set-github-variables.sh` (typed `VARIABLES`) sets `AWS_RELEASE_ROLE_ARN` and `AWS_ARTIFACTS_BUCKET` on `Cantara/julebrus`.
3. **DNS.** Create the A record `xmas.cantara.no` -> stack output `ElasticIp`.
4. **SNS.** Confirm the subscription e-mail sent to the alarm address.
5. **Box setup.** `bash scripts/julebrus.sh setup` (typed `SETUP`): uploads `scripts/` and `systemd/` to the artifacts bucket and installs them on the box over SSM (the instance needs a few minutes after step 1 to finish installing packages). The script ends with a separate remote check line that says OK or FAILED; `start-session` does not return the remote exit status, so read it.
6. **TLS.** Once the name resolves: `bash scripts/julebrus.sh tls <email>` (typed `TLS`); same remote check line.
7. **Google secret.** `bash scripts/google-oauth.sh create`, then `bash scripts/google-oauth.sh status`. Do this BEFORE the first release: the app cannot start without a client id, and prestart stops with a clear message in the journal.
8. **Decide who may sign in, then set the admins.** Default is open: any Google account. To restrict to company domains: `bash scripts/julebrus.sh env set JULEBRUS_ALLOWED_DOMAINS=cantara.no` (comma separated). Then `env set JULEBRUS_ADMINS=a@example.org,b@example.org`. Each restarts the app (before the first release it only restarts a service that has nothing to start yet; the check line may say FAILED then, which is expected).
9. **First release.** Push a tag on `main`, for example `git tag v0.1.0 && git push origin v0.1.0`. The `build` and `release` jobs run; the `upload` job waits for the Environment's reviewer. The box picks the release up within 5 minutes, or run `bash scripts/julebrus.sh update` (typed `UPDATE`).
10. **Verify.** `bash scripts/julebrus.sh status` and `update-log`; `https://xmas.cantara.no/actuator/health` returns `{"status":"UP"}`; sign in with Google from a browser; `bash scripts/julebrus.sh diag` if not.
11. **Turn on database deletion protection.** `DB_DELETION_PROTECTION=true bash scripts/00-provision.sh <alarm-email>` (an update; the change set should show only the database as a modification, not a replacement).
12. **External uptime check.** Set up a check on `https://xmas.cantara.no/actuator/health` from outside (any uptime monitor) that e-mails the owner. We do not build one: the CloudWatch alarms see the instance and the database, not the app.

Later changes to `template.yaml`: rerun `00-provision.sh`; the change set column `Replacement` shows whether anything is destroyed and recreated. On an update the VPC, subnets, AMI, instance type, Elastic IP choice and deletion protection keep their previous values unless you pass them.

### Rolling the AMI deliberately
The image id is fixed at create time, so routine updates never replace the instance. To move to a newer AL2023 image (this REPLACES the instance: a new box that needs `setup`, `tls` again, and the Elastic IP moves with it): `NEW_AMI=true bash scripts/00-provision.sh <alarm-email>`, read the Replacement column, and only then type `DEPLOY`. Prefer doing it outside the season. Patching in place with `dnf update` over SSM is the lighter alternative.

## GitHub setup

The release workflow builds with no AWS access. Only its `upload` job, which runs in the Environment `production`, can assume the AWS role.

1. Settings > Environments > New environment `production`. Add a required reviewer. Under "Deployment branches and tags" choose selected branches and tags and add `main` and the tag pattern `v*`.
2. Settings > Rules > Rulesets: a tag ruleset for `v*` that restricts who can create, update and delete those tags (and blocks force pushes). A tag decides what gets released, so protect it like a branch.
3. The IAM trust is `token.actions.githubusercontent.com:sub = <GitHubSubjectPrefix>:environment:production`. The ref (tag or branch) is not part of an environment job's subject, so IAM cannot also check for a `v*` tag; steps 1 and 2 are what restrict this, which is why they are part of the setup and not optional.
4. `GitHubSubjectPrefix` defaults to `repo:Cantara/julebrus`. If the organisation uses GitHub's immutable subject format, read the real prefix with `gh api repos/Cantara/julebrus/actions/oidc/customization/sub` and pass it as `GITHUB_SUBJECT_PREFIX` to `00-provision.sh` (create, or update). The role trust then reads `<that prefix>:environment:production`. The symptom of a wrong prefix is "not authorized to perform sts:AssumeRoleWithWebIdentity" in the upload job.
5. The workflow's actions are pinned by version tag, NOT by commit SHA: `actions/checkout@v7`, `actions/setup-node@v4`, `actions/setup-java@v4`, `actions/upload-artifact@v4`, `actions/download-artifact@v4`, `softprops/action-gh-release@v2`, `aws-actions/configure-aws-credentials@v4`. Pin them by SHA if that matters to you (Renovate can do it).

## Rollback and failure behaviour

- Automatic: if the service does not start (for example a secret is missing) or `/actuator/health` is not up within about 2 minutes, the updater points `current` back at the previous release, restarts it, and remembers the failed version (same sha256) so the timer does not restart the app every 5 minutes for the same jar. Publish a new tag to retry. On a first install with nothing to go back to it removes the `current` link and stops the service.
- It never moves to a LOWER version than the installed one by itself. A deliberate downgrade (an older `latest.json`) needs the version written once into `/var/lib/julebrus/allow-downgrade` on the box.
- A failure that appears LATER, after a release passed its health check, is not rolled back automatically. The systemd service restarts a crashed app (every 30 s, at most 5 times in 10 minutes); beyond that, someone has to act, which is why the external uptime check (runbook step 12) matters.
- Manual: push a new tag from the last good commit (versions only move forward).
- Before a release with a database migration, take an RDS snapshot first (console or CLI); Flyway migrations are not rolled back with the jar.

## Restore test

Do it once before the season: take a manual snapshot (or use an automated one), restore it to a NEW instance
`julebrus-db-restore` (never over the live one), check that it comes up and has the tables, then delete the test instance.
The 14-day retention allows point-in-time restore. Not yet done.

## After the season

The app closes on 23 Dec 2026 (the window is enforced by the app). The instance can then be stopped, not deleted: the
data stays in RDS and on the disk, and starting it again resumes service (with the Elastic IP the address is kept; without it
the A record must be updated).

Rough monthly cost, an ESTIMATE from list prices in eu-north-1 and not from the AWS calculator (check before relying on it):

| Item | While running | Instance stopped |
|---|---|---|
| EC2 t4g.small | about 12-14 USD | 0 |
| EBS 20 GB gp3 | about 2 USD | about 2 USD |
| RDS db.t4g.micro + 20 GB gp3 | about 15-18 USD | same (RDS also auto-restarts after 7 stopped days) |
| Public IPv4 / Elastic IP | about 3.6 USD | about 3.6 USD (idle EIP is charged too) |
| Secrets Manager (2 secrets), alarms, S3 | about 1.5 USD | about 1.5 USD |
| Total | roughly 35-40 USD | roughly 22-25 USD |

To go lower, snapshot the database and delete the stack later (deletion protection must be turned off first; the stack
keeps a final snapshot, and the artifacts bucket and the `julebrus/db` secret are retained). That is a separate decision and not scripted here.

## Not verified

Everything here was written and checked offline; nothing was run against AWS, GitHub or a real box.

- Not run at all: `00-provision.sh`, `julebrus.sh`, `google-oauth.sh`, `set-github-variables.sh`, `box-setup.sh`, the template deployment, the release workflow, certbot, the real SSM sessions.
- The template was not linted with `cfn-lint` (not installed) nor validated by CloudFormation. It was checked by a YAML parse, a reference check (every Ref/GetAtt/Sub target exists, no unused parameter) and by hand against the proven template this one is adapted from. In particular the dynamic reference `{{resolve:secretsmanager:...}}` for the RDS master password, the `DeletionProtection` taking a string parameter, and the interplay of the retained secret with a stack that is deleted and created again are untested.
- `release.yml` was not linted (`actionlint` not installed) and has never run. Whether the job-level `if` on repository variables, the Environment gate and the artifact hand-off between the jobs behave as intended is untested. The versions-maven-plugin version (2.18.0) was not checked against Maven Central.
- The Cantara organisation's OIDC subject format is unknown (see "GitHub setup").
- That `SERVER_FORWARD_HEADERS_STRATEGY=framework` produces `https://` redirect URIs behind nginx was not exercised with the real app; check the redirect in step 10.
- Quoting of the database and Google values in the systemd `EnvironmentFile` was tested only by looking at the produced file (a quote, backslash, dollar and percent in a fake password), not with systemd itself. The generated password is alphanumeric, so this matters only for the Google secret.
- The HSTS insertion into certbot's 443 block was tested with `sed` on a sample block, not with certbot's real output.
- Only these were exercised locally, with fake `aws`, `curl`, `systemctl`: the swap/rollback logic of `julebrus-update.sh` (fresh install, same version, health failure, restart failure, retry of a failed version, sha mismatch, downgrade refusal and the one-time override, upgrade, a held lock, first install failing by health and by restart), the allow-list of `julebrus-env.sh`, and the file `julebrus-prestart.sh` produces (and its failure on a missing field). The update test ran as a non-root user.
- `julebrus.sh` remote checks print OK/FAILED lines from the box, but the SSM session itself does not return the remote exit status.
- Instance type availability, the Elastic IP quota and the existence of the GitHub OIDC provider are checked at run time by the preflight, not known.
- The cost table is an estimate.
