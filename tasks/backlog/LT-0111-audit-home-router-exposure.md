---
id: LT-0111
title: Audit home router exposure for LiftTrax
status: ready
track: platform
priority: high
effort: small
created: 2026-10-31
updated: 2026-10-31
owner: unassigned
depends_on: [LT-0099]
---

# LT-0111: Audit home router exposure for LiftTrax

## Why

The PC firewall and private DuckDNS address support the intended home-only
design, but the router's port forwards, UPnP mappings, remote administration,
firmware, and wireless security were not inspected during the local review.
Public internet scans target addresses even when a private hostname is used.

## Outcome

The home router is verified to expose no LiftTrax, database, Remote Desktop, or
file-sharing service to the internet, while normal trusted home Wi-Fi access
continues to work.

## Scope

- In scope: router/NAT/UPnP inventory; WAN administration; expected closed
  ports; firmware and wireless-security review; authorized external reachability
  check; rollback notes.
- Out of scope: enabling away-from-home access, replacing the router without a
  separate decision, storing router credentials in the repository, or making
  disruptive firmware/network changes without approval.

## Acceptance criteria

- [ ] Access the router through its trusted local administration path without
      copying credentials, configuration exports, public IP addresses, or
      recovery secrets into source control or chat.
- [ ] Inventory manual port forwards, UPnP/NAT-PMP mappings, DMZ host settings,
      WAN administration, exposed management protocols, and firewall state.
- [ ] Verify there is no internet forwarding or mapping for ports 80, 443, 8080,
      18080, 5432, 3389, 139, or 445 to the LiftTrax PC.
- [ ] Disable unexpected mappings and WAN administration only after explaining
      device impact and obtaining approval; preserve a safe rollback path.
- [ ] Review whether UPnP, WPS, file sharing, and remote management are needed.
      Disable unused features or document why each retained feature is safe.
- [ ] Confirm supported firmware is current before any update. Back up settings
      securely and obtain approval before a disruptive firmware installation.
- [ ] Verify WPA2-AES or WPA3 is used with a strong unique Wi-Fi/router password;
      document guest/IoT isolation decisions without recording credentials.
- [ ] From an authorized external connection such as the user's phone on mobile
      data, confirm the home-only LiftTrax address and relevant ports are
      unreachable. Do not rely solely on an internal test.
- [ ] Reconfirm trusted HTTPS, sign-in, history, and a health request on home
      Wi-Fi after any router change.
- [ ] Record a dated, secret-free audit and note that LT-0100 is required before
      deliberately enabling access away from home.

## Notes

The current hostname resolves to a private RFC1918 address, which is good but is
not a complete router audit. The desired external test result for this task is
that LiftTrax remains unreachable away from home.
