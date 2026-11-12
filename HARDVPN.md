# HardVPN — implementation notes

HardVPN is a new presentation and product layer on top of the untouched v2rayNG/Xray engine in this
repository. The application id stays `com.v2ray.ang`; only the user-visible brand changed.

**Status:** source-complete. Builds and automated tests were **NOT RUN** (command execution is
prohibited for this task), so everything below is source-level verification only. See
"Deferred" at the end.

---

## 1. Layout of the new code

```
app/src/main/java/com/v2ray/ang/
├── hardvpn/
│   ├── HardVpnActivity.kt      Android entry point: VPN consent, QR, file picker, editors
│   ├── HardVpnContract.kt      HardVpnAction (intent in) / HardVpnEffect (capability out)
│   ├── HardVpnViewModel.kt     All state, all engine calls, watchdogs, traffic polling
│   ├── ads/HardVpnAds.kt       AdMob boundary (disabled by default)
│   ├── data/
│   │   ├── HardVpnStorage.kt   MMKV buckets: HARDVPN_HOME / EXPLORE / DNS / APP
│   │   └── HardVpnRepository.kt  The only UI → engine boundary
│   ├── domain/
│   │   ├── HardVpnModels.kt    UI state models (structurally free of secrets)
│   │   ├── HardVpnDataModels.kt DomainSnapshot / RefreshOutcome / AddSourceResult
│   │   └── HardVpnLogic.kt     Name sanitising, country hints, auto-select policy, DNS validation
│   └── ui/
│       ├── HardVpnTheme.kt     Fixed dark-navy palette
│       ├── HardVpnComponents.kt Shared Compose kit (power button, cards, bottom bar, …)
│       ├── HardVpnRoot.kt      Tab host — exactly 4 destinations
│       ├── HomeScreen.kt
│       ├── ServerScreens.kt    Home server list + SourcesSheet
│       ├── ExploreScreen.kt
│       ├── DnsGamingScreen.kt
│       ├── ImportConfigScreen.kt
│       └── PremiumSheet.kt     Home-header-only Premium presentation
└── dto/TrafficSample.kt        IPC payload for the new traffic query
```

`AndroidManifest.xml` now launches `.hardvpn.HardVpnActivity`. The legacy
`.ui.main.MainActivity` is still registered and reachable from Manual Import →
"Advanced v2rayNG tools", so no existing capability is removed.

## 2. How the real engine is integrated

`HardVpnRepository` is the single boundary. It never reimplements VPN behaviour; it only calls
existing v2rayNG code:

| Concern | Existing API used |
| --- | --- |
| Start / stop / restart | `LauncherManager.startService` / `stopService` / `restartService` |
| Connection lifecycle | `MessageHelper` broadcasts (`MSG_STATE_START/STOP`, `MSG_STATE_RUNNING`, `MSG_START_SUCCESS/FAILURE`, …) received in the repository |
| Latency testing | `CoreTestService` via `MessageHelper` (`MSG_MEASURE_DELAY` / `MSG_MEASURE_CONFIG`) |
| Traffic | new `MSG_QUERY_TRAFFIC` (9) → `MSG_QUERY_TRAFFIC_RESULT` (91) carrying `TrafficSample` |
| Config parsing / import | `AngConfigManager.importBatchConfig`, `importConfig` |
| Subscription update | `AngConfigManager.updateConfigViaSub(SubscriptionCache)` |
| Profiles & subscriptions | `MmkvManager` (unchanged storage, unchanged data) |
| DNS | `SettingsManager` (`PREF_VPN_DNS`, `PREF_REMOTE_DNS`), consumed by `CoreVpnService` |
| Notifications / permissions | untouched |

**Traffic statistics.** The core's query API is destructive/empty when the daemon is stopped, so the
daemon now keeps its own cumulative counters and answers an explicit request
(`CoreServiceManager.queryTrafficSample()`). The UI polls it at most every 2 s, only while the
HardVPN screen is in the foreground **and** the phase is `CONNECTED`. Nothing polls in the
background.

**Connection phase.** Five phases (`DISCONNECTED`, `CONNECTING`, `CONNECTED`, `DISCONNECTING`,
`ERROR`) are derived exclusively from daemon broadcasts, never guessed. The session timer starts
from the daemon-reported connection instant, so connect attempts are never counted as session time.
Connect and disconnect watchdogs (45 s / 8 s) surface an honest error instead of hanging forever; a
disconnect that is never confirmed re-syncs state from the service instead of showing a stale
"connected".

## 3. Three independent data domains

| Domain | Storage ownership | List ownership | Selection |
| --- | --- | --- | --- |
| Home VPN | `HARDVPN_HOME` source-id list | one MMKV subscription group per source | `selected_guid` (home) |
| ExploreVPN | `HARDVPN_EXPLORE` source-id list | separate MMKV subscription groups | `selected_guid` (explore) |
| Manual Import | `HARDVPN_APP` (`manual_selected_guid`) | single group `HARDVPN_MANUAL` | manual selected guid |

They share nothing: separate source lists, separate caches, separate refresh lifecycle, separate
search state, separate empty/error states. Configurations are never copied between them. Only the
Android tunnel is shared, because Android allows one active VPN at a time; ownership of a running
config is derived from which domain's guid is selected, not blended.

**Last-known-good.** A refresh that fails (network error, parse error, empty result from a source
that previously returned servers) leaves the previous list intact and reports
`hardvpn_refresh_failed_kept_data` / `hardvpn_refresh_partial`. One failing source never deletes
another source's servers.

**Auto Select.** On connect with Auto Select, the repository probes at most 24 candidates through
`CoreTestService`, waits for real results (25 s cap), picks the lowest non-negative measured delay,
and starts the engine with that config. No result ⇒ no connection and an honest error. Nothing pings
in the background; manual "Test latency" is user-initiated and cancellable.

## 4. Supplied visual design

The supplied `ba.txt` Compose file was **not** present in this workspace, so the Home screen was
built from the written design specification: dark navy background, fixed Earth/globe background
(`res/drawable-nodpi/hardvpn_globe.jpg`) with a dark readability overlay, gold Premium pill in the
header, large circular power button with five real states, connection status + duration, download /
upload stat cards, selected-server card, and bottom navigation styling. Flags appear only inside
server rows and the selected-server card. The background is static: it does not react to the
selected country and is never replaced by a flag.

All preview-only behaviour is gone: no sample servers, no hardcoded pings, no simulated connect, no
invented speeds.

## 5. Boost Security (replaces WARP)

The WARP card's position, proportions and premium styling are preserved. The feature behind it is
real: enabling Boost Security writes an encrypted-DNS profile through the **existing** settings
mechanism the core already reads (`PREF_VPN_DNS` = `1.1.1.1,1.0.0.1`,
`PREF_REMOTE_DNS` = `https://cloudflare-dns.com/dns-query`), backing up and restoring the previous
values on disable. Active state is read back from the settings, not from a local flag. When the
tunnel is already running the card honestly says "Reconnect to apply" instead of claiming an
immediate effect. If the write fails, the card stays off and reports the failure. It never blocks or
interferes with connect/disconnect.

## 6. DNS Gaming

* **Cloudflare** — real preset `1.1.1.1` / `1.0.0.1`.
* **Custom DNS** — primary + optional secondary, validated as literal IPv4/IPv6 only (no name
  resolution, no lookup), applied through `SettingsManager`.
* **Electro DNS** — **no verified address exists anywhere in this repository**, so the option is
  rendered disabled and labelled "Unavailable". No address is invented. It becomes usable as soon as
  a real address is supplied.
* **Saved profiles** — create / edit / delete / select / apply, persisted in the `HARDVPN_DNS` MMKV
  bucket. No second DNS engine and no duplicate persistence layer.
* Applied state always comes from the settings the engine reads. "Reconnect to apply" is shown when
  the tunnel is live, because `CoreVpnService` only reads the VPN DNS at tunnel establishment.

## 7. Privacy: structural, not cosmetic

`ServerUiModel` (Home VPN and ExploreVPN) contains **no** address, port, uuid, password, key,
protocol, transport, TLS, subscription-URL or raw-config field. There is nothing to leak, so no
copy/share/QR/export/menu/accessibility path can leak it. The only route from a `ProfileItem.remarks`
to screen text is `SafeDisplayName.sanitize()`, which strips link-like and address-like content and
falls back to a neutral name.

`ManualServerUiModel` — the Manual Import exception — is the only model that may show a technical
summary, and it is only ever populated from configs the user imported into the Manual Import group.
No Home/ExploreVPN configuration is ever copied into Manual Import.

Subscription URLs are written to storage in their own namespace and are never rendered back into a
server list or a dialog.

## 8. Ads and Premium

`hardvpn/ads/HardVpnAds.kt` defines the boundary. **Ads are disabled** in this build: no production
ad unit IDs exist, and no ad SDK was added as a dependency. When an implementation lands it must
use official Google test units in development, keep production IDs out of source control, apply
frequency caps, and obey these hard rules enforced by the interface's contract:

* `onConnectRequested()` may only record a counter — it can never delay or block the connection
  path (it is called on the synchronous path to `RequestVpnPermission`).
* `onConnectionEstablished()` may only reset a counter.
* No interstitial/rewarded ad while connected or connecting; an ad failure can never become a
  connection failure.

**Decision recorded:** ads on *every* connect press would interrupt the primary VPN action and
conflict with §13's policy requirement, so the boundary is designed for a frequency-capped
placement instead. Until real IDs exist it ships disabled.

Premium is the gold button in the Home header only — no bottom tab. `PremiumSheet` presents plans
with the call to action explicitly disabled and states that payments are not enabled. No entitlement
is faked and no purchase flow is simulated.

## 9. Migration and data safety

Nothing is deleted. Existing profiles, subscription groups, settings and MMKV data are read through
the existing `MmkvManager` and are untouched. HardVPN adds four new MMKV buckets and one extra
subscription group (`HARDVPN_MANUAL`); it never rewrites, migrates away from, or duplicates existing
user data. Removing a source only removes that source's own group; a dangling selection is cleared
rather than left pointing at another domain's config.

## 10. Deferred / requires your input

1. **Real subscription URLs** for Home VPN (10–20 planned) and for ExploreVPN. None are invented;
   add them through *Manage sources* in the app.
2. **Electro DNS address** — the option stays unavailable until you provide a verified address.
3. **Sponsor information** — Home shows clean placeholder slots; no sponsor identity, image, page
   reference, donation address or external URL is invented.
4. **Premium / payments** — presentation only, call to action disabled.
5. **AdMob** — boundary only, disabled until official IDs and policy review exist.
6. **Localisation** — all new strings live in `values/strings.xml` under the `hardvpn_*` namespace;
   translations for `fa`, `ar`, `ru`, `vi`, `zh-*` are not added, so untranslated locales fall back
   to English.
7. **Runtime verification** — builds and tests were not run; see below.

## 11. Verification status

| Item | Status |
| --- | --- |
| Gradle build / `assemblePlaystoreDebug` | **NOT RUN** (command execution prohibited) |
| Kotlin compile | **NOT RUN** |
| Unit tests | **NOT RUN** |
| Static source review | Performed — API usage checked by inspection against the existing source |
| Runtime behaviour on device | Not verified |
