# Watch Party teilt Backend und Protokoll mit dem Desktop-Fork

Der Watch-Party-Port (aus `NuvioDesktop-dev`, Stand `baad4577`, 2026-07-17) verbindet sich
mit demselben self-hosted Supabase-Realtime-Projekt wie der Desktop-Fork
(`NUVIO_WATCHPARTY_SUPABASE_URL/_ANON_KEY`, injiziert über `local.properties` →
`GenerateRuntimeConfigsTask`). Entschieden, weil plattformübergreifende Räume
(Desktop-Host, Mobile-Gast und umgekehrt) der Zweck des Ports sind; ein getrenntes
Projekt hätte das Feature entwertet.

## Consequences

- **Protokoll-Lockstep:** Beide Forks sprechen dasselbe Broadcast-/Presence-Format auf
  dem Channel `watchparty:<code>`. Änderungen am Protokoll (neue Felder, geänderte
  Semantik, Presence-Budget-Parameter) müssen zeitnah in beide Repos portiert werden,
  sonst divergieren gemischte Räume.
- Der Supabase-Keepalive-Ping läuft ausschließlich in der Desktop-Fork-CI
  (`upstream-sync.yml`); Mobile besitzt bewusst keinen eigenen.
- Ohne konfigurierte Secrets ist das Feature inaktiv (`WatchPartySupabaseProvider.isConfigured`)
  — es gibt kein zusätzliches Flavor-Gating, da dieser Fork keine Store-Releases macht.
