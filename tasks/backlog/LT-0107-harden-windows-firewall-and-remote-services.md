---
id: LT-0107
title: Harden Windows firewall and remote services
status: ready
track: platform
priority: high
effort: medium
created: 2026-10-31
updated: 2026-10-31
owner: unassigned
depends_on: [LT-0099]
---

# LT-0107: Harden Windows firewall and remote services

## Why

The 2026-10-31 security review found enabled Public-network allow rules for an
older Caddy executable, Remote Desktop rules covering every network profile,
and File and Printer Sharing rules covering Public networks. The current Caddy
binary has the intended Private/LocalSubnet rule, and RDP/SMB were not listening
during the review, but dormant broad rules can become exposure later.

## Outcome

Windows permits only the inbound services the user deliberately needs, on the
narrowest appropriate network profile, ports, source addresses, and executable.
Obsolete rules and unused remote services no longer create latent exposure.

## Scope

- In scope: before/after firewall audit; stale Caddy rules; RDP and File and
  Printer Sharing decisions; listener verification; reboot verification;
  rollback documentation.
- Out of scope: router changes, away-from-home access, disabling unrelated
  applications, changing LiftTrax data, or silently removing a service the user
  confirms they need.

## Acceptance criteria

- [ ] Save a secret-free, ignored before-state audit of relevant firewall rules,
      listeners, executable paths, network profiles, and service states.
- [ ] Disable or remove Public/Any-port rules for obsolete Caddy executables and
      prove that no current process relies on them.
- [ ] Retain exactly the required LiftTrax proxy allowance: the current verified
      Caddy executable, inbound TCP 80/443, Private profile, LocalSubnet sources.
- [ ] No LiftTrax rule exposes Java backend, Postgres, UDP, arbitrary ports, or
      Public-profile access.
- [ ] Confirm with the user whether Remote Desktop is needed before changing it.
      If unused, disable RDP and its inbound rules; if required, restrict it to
      the trusted home network and retain Network Level Authentication.
- [ ] Confirm whether File and Printer Sharing is needed before changing it. If
      unused, disable its inbound rules and services; if required, remove Public
      exposure and restrict it to the trusted home network.
- [ ] After changes and after a reboot, enumerate effective non-loopback
      listeners and explain every allowed inbound service.
- [ ] Windows Firewall remains enabled with inbound traffic blocked by default
      on Domain, Private, and Public profiles.
- [ ] Trusted LiftTrax HTTPS still passes health, sign-in, history, and phone
      connectivity checks without reopening broader rules.
- [ ] Document exact rollback commands/settings without storing firewall exports
      containing machine-specific data in source control.

## Notes

Administrator elevation is expected. Apply changes only after resolving whether
RDP and sharing are intentionally used. Do not treat an inactive listener as
proof that an enabled broad firewall rule is safe to keep.
