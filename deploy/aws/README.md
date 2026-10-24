# Julebrus: AWS deployment

One small EC2 box with nginx (HTTPS by certbot) in front of the Spring Boot jar, RDS PostgreSQL, an S3 artifacts bucket
for releases. The season runs 1 Nov to 23 Dec 2026. Whoever runs `aws` against the account is an operator; every
command below is run by a person (mutating scripts ask for a typed word first). Nothing in this folder has been run
against AWS yet, see "Not verified".

## Decisions

| Topic | Decision |
|---|---|
| Account, region | A sponsored AWS account (profile `neuron`), `eu-north-1` |
| Stack | CloudFormation stack `julebrus`; every resource is named and tagged `julebrus-*` / `Project=julebrus` |
| Host name | `xmas.cantara.no`. DNS is not in the stack: the A record is made by hand to the stack's Elastic IP |
| Access to the box | SSM Session Manager only. No key pair, no inbound SSH; only 80 and 443 inbound |
| HTTPS | nginx + certbot on the box; the jar listens on 127.0.0.1:8080 only |
| Database | RDS PostgreSQL 16, db.t4g.micro, single-AZ, encrypted, private, 14-day backups, deletion protection, final snapshot. The master password lives in the RDS-managed Secrets Manager secret |
| Storage | The app stores only database rows, so there is no documents bucket |
| Releases | Pull. A tag `vX.Y.Z` runs `.github/workflows/release.yml`, which uploads `releases/<v>/app.jar` and `latest.json` (with sha256) to `julebrus-artifacts-<account>` through GitHub OIDC. A systemd timer on the box checks every 5 minutes, verifies the sha256, swaps, health-checks `/actuator/health` and rolls back on failure |
| OIDC trust | Role `julebrus-release-uploader` may be assumed only from a `v*` tag of `Cantara/julebrus` (`repo:Cantara/julebrus:ref:refs/tags/v*`) and can only write `releases/*` and `latest.json` |

## What the app expects and how this stack provides it

| App setting | Provided by |
|---|---|
| `JULEBRUS_DB_URL` | `/etc/julebrus/app.env` (non-secret, written from the stack; `sslmode=require`) |
| `JULEBRUS_DB_USER`, `JULEBRUS_DB_PASSWORD` | fetched at every start from the RDS-managed secret into `/run/julebrus/secrets.env` (tmpfs, 0640) by `julebrus-prestart.sh` |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | same prestart, from the secret `julebrus/google-oauth` `{client-id, client-secret}` that `scripts/google-oauth.sh create` stores |
| `JULEBRUS_ALLOWED_DOMAINS` (empty = any Google account), `JULEBRUS_ADMINS`, `JULEBRUS_CONTACT_EMAIL` | `/etc/julebrus/app.env`, empty at first; changed with `julebrus.sh env set KEY=VALUE` (allow-list of exactly these three keys, the file is never printed) |
| `SERVER_FORWARD_HEADERS_STRATEGY=framework` | `/etc/julebrus/app.env` (see below) |

Notes on the app as read from the repository (branch point of this work):

- `application.yml` has no `server.forward-headers-strategy`, and `SecurityConfig` does not handle forwarded headers. Behind
  nginx Spring would then build `http://` redirect URIs and Google login would fail. Because `backend/` is not changed
  here, the stack sets the Spring relaxed-binding variable `SERVER_FORWARD_HEADERS_STRATEGY=framework` in `app.env`, and
  nginx sends `X-Forwarded-Proto`, `X-Forwarded-Host` and `X-Forwarded-For`. If the app later sets the property itself, the variable can stay.
- The app reads no sponsor settings. `JULEBRUS_CONTACT_EMAIL` is not read by the app at the time of writing either; it is in the allow-list and in `app.env` so it works the day the app starts reading it, and is harmless until then.
- The pom carries `0.1.0-SNAPSHOT`. The release workflow sets the version from the tag on the build checkout only (`versions:set`), so the jar is `julebrus-<tag version>.jar`.
- The Google OAuth redirect URI to register is `https://xmas.cantara.no/login/oauth2/code/google`.

## Layout

```
DNS A record  ->  Elastic IP  ->  EC2 t4g.small (AL2023 arm64, Java 21)
                                    nginx :80/:443 (certbot) -> Spring Boot 127.0.0.1:8080
                                    julebrus-update.timer pulls releases from S3 every 5 min
RDS PostgreSQL 16 (db.t4g.micro, single-AZ, encrypted, private)
S3  julebrus-artifacts-<account>   releases/<v>/app.jar, latest.json, deploy/ (scripts and units); written by the OIDC role / the operator
Secrets Manager  julebrus/google-oauth (made by google-oauth.sh), RDS-managed master secret
SNS + CloudWatch alarms            instance status check, RDS free storage, RDS CPU
```

Files: `template.yaml` (the stack), `scripts/00-provision.sh` (preflight + change set), `scripts/set-github-variables.sh`,
`scripts/google-oauth.sh`, `scripts/julebrus.sh` (operator, via SSM), and for the box `scripts/box-setup.sh`,
`julebrus-prestart.sh`, `julebrus-update.sh`, `julebrus-env.sh`, `systemd/julebrus.service`, `julebrus-update.service|timer`.
The scripts are not on PATH; usage is in each header.

## What the project owner must provide

- An e-mail address for the alarms (CloudWatch -> SNS).
- A Google OAuth client (type: web application) in a Google Cloud project, with the authorised redirect URI
  `https://xmas.cantara.no/login/oauth2/code/google`, and its client id and secret (typed into `google-oauth.sh create`).
- The DNS A record for `xmas.cantara.no` (Cantara's DNS) to the Elastic IP.
- Whether to restrict sign-in: `JULEBRUS_ALLOWED_DOMAINS` (empty allows any Google account) and the admin e-mails for `JULEBRUS_ADMINS`.

## Runbook (an operator with admin access to the account, in order)

Run from `deploy/aws/` of a checkout. `export PROFILE=neuron` once.

1. **Provision.** `bash scripts/00-provision.sh <alarm-email> [true|false]`. Read-only preflight (our names are free, the GitHub
   OIDC provider exists, default VPC and a subnet in a zone that offers the instance type, the Elastic IP quota), then a change
   set that lists exactly what will be created, then a typed `DEPLOY`. Takes about 10 minutes (RDS). The second argument is
   `UseElasticIp`; pass `false` if the allocation is refused (the instance's own IP changes when it is stopped and started).
   If the GitHub organisation uses the immutable OIDC subject format, set `GITHUB_SUBJECT_PREFIX` (see the template parameter).
2. **GitHub variables.** `bash scripts/set-github-variables.sh` (typed `VARIABLES`) sets `AWS_RELEASE_ROLE_ARN` and `AWS_ARTIFACTS_BUCKET` on `Cantara/julebrus`.
3. **DNS.** Create the A record `xmas.cantara.no` -> stack output `ElasticIp`.
4. **SNS.** Confirm the subscription e-mail sent to the alarm address.
5. **Box setup.** `bash scripts/julebrus.sh setup` (typed `SETUP`): uploads `scripts/` and `systemd/` to the artifacts bucket and installs them on the box over SSM (the instance needs a few minutes after step 1 to finish installing packages).
6. **TLS.** Once the name resolves: `bash scripts/julebrus.sh tls <email>` (typed `TLS`).
7. **Google secret.** `bash scripts/google-oauth.sh create`, then `bash scripts/google-oauth.sh status`. Do this BEFORE the first release: the app cannot start without a client id, and prestart stops with a clear message in the journal.
8. **Settings.** Optional: `bash scripts/julebrus.sh env set JULEBRUS_ADMINS=a@example.org,b@example.org` (and the other two keys); each restarts the app.
9. **First release.** Push a tag on `main`, for example `git tag v0.1.0 && git push origin v0.1.0`. The workflow tests, builds and uploads; the box picks it up within 5 minutes, or run `bash scripts/julebrus.sh update` (typed `UPDATE`).
10. **Verify.** `bash scripts/julebrus.sh status` and `update-log`; `https://xmas.cantara.no/actuator/health` returns `{"status":"UP"}`; sign in with Google from a browser; `bash scripts/julebrus.sh diag` if not.

Later changes to `template.yaml`: rerun `00-provision.sh`; the change set column `Replacement` shows if anything is destroyed and recreated. Note that the AMI parameter follows "latest AL2023", so an update can propose replacing the instance; cancel at the prompt unless that is intended.

## Rollback

- Automatic: a release whose `/actuator/health` is not up within about 2 minutes is replaced by the previous release, and the failed version is remembered (same sha256) so the timer does not restart the app every 5 minutes for the same jar. Publish a new tag to retry.
- Manual: push a new tag from the last good commit (versions only move forward), or on the box over SSM `ln -sfn /opt/julebrus/releases/<old> /opt/julebrus/current && systemctl restart julebrus` (the timer then sees `latest.json` ahead of `current` and tries again unless that version has failed before, so also fix `latest.json` by tagging).
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
keeps a final snapshot and the artifacts bucket is retained). That is a separate decision and not scripted here.

## Not verified

Everything here was written and checked offline; nothing was run against AWS, GitHub or a real box.

- Not run at all: `00-provision.sh`, `julebrus.sh`, `google-oauth.sh`, `set-github-variables.sh`, `box-setup.sh`, the template deployment, the release workflow, certbot, the real SSM sessions.
- The template was not linted with `cfn-lint` (not installed) nor validated by CloudFormation. It was checked by a YAML parse and by hand against the proven template this one is adapted from.
- The `release.yml` was not linted (`actionlint` not installed) and has never run; action versions follow the repo's `ci.yml`.
- GitHub OIDC subject format of the Cantara organisation is unknown. If role assumption fails with "not authorized to perform sts:AssumeRoleWithWebIdentity", read the real prefix with `gh api repos/Cantara/julebrus/actions/oidc/customization/sub` and redeploy with `GITHUB_SUBJECT_PREFIX`.
- That `SERVER_FORWARD_HEADERS_STRATEGY=framework` produces `https://` redirect URIs behind nginx was not exercised with the real app; check the redirect in step 10.
- That the RDS-generated master password survives the systemd `EnvironmentFile` quoting for every character was tested with a quote, backslash, dollar and percent only, with a fake secret and not with systemd itself.
- Only the swap/rollback logic of `julebrus-update.sh`, the allow-list of `julebrus-env.sh` and the file produced by `julebrus-prestart.sh` were exercised locally, with fake `aws`, `curl` and `systemctl`.
- Instance type availability, the Elastic IP quota and the existence of the GitHub OIDC provider are checked at run time by the preflight, not known.
- The cost table is an estimate.
