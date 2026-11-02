---
id: LT-0109
title: Protect local data with verified disk encryption
status: ready
track: platform
priority: high
effort: small
created: 2026-10-31
updated: 2026-10-31
owner: unassigned
depends_on: [LT-0092]
---

# LT-0109: Protect local data with verified disk encryption

## Why

Application authentication and HTTPS do not protect Postgres files, local
backups, ignored credentials, or browser data if the PC or storage device is
stolen. The 2026-10-31 review could not conclusively verify BitLocker or Windows
Device Encryption status for the system drive.

## Outcome

The volumes containing LiftTrax data, credentials, and backups have verified
at-rest encryption, with a safe recovery-key plan and documented residual risk.

## Scope

- In scope: read-only encryption inventory; supported BitLocker/Device
  Encryption path; recovery-key handling; LiftTrax data and backup locations;
  reboot and recovery verification; operator documentation.
- Out of scope: copying recovery keys into the repository or chat, moving the
  database, formatting drives, encrypting without approval, or claiming that
  disk encryption replaces backups.

## Acceptance criteria

- [ ] Inventory the system drive and every volume containing the canonical
      Postgres data, ignored LiftTrax credentials, HTTPS state, backups, and
      exports; record status without exposing sensitive paths or contents.
- [ ] Determine the Windows-supported encryption mechanism and prerequisites,
      including TPM, edition, account, and recovery-key requirements.
- [ ] Before enabling or changing encryption, explain reboot/recovery risk,
      confirm a tested recovery-key storage location outside the protected PC,
      and obtain explicit user approval.
- [ ] Never print, commit, upload to task artifacts, or place a recovery key in
      logs or chat.
- [ ] Verify protection is active and complete after encryption and after a
      reboot using both Windows status and an independent read-only check where
      available.
- [ ] Confirm LiftTrax, Postgres, HTTPS startup, sign-in, history, and backup
      validation still work without changing application rows or ownership.
- [ ] Identify any unencrypted removable, network, or archive backup copies and
      document a supported protection or deletion decision for each.
- [ ] Document recovery and suspension steps for firmware/Windows maintenance,
      plus the difference between encryption, backup, and account security.
- [ ] If repository scripts change, add focused non-destructive tests and run
      `./gradlew.bat qualityGate`; otherwise record the completed system checks.

## Notes

This task must be recoverable. Do not enable encryption until the user can
recover the machine without relying on a key stored only on that same machine.
