# Timer Balance golden timeline

`timeline.csv` is copied verbatim from `timer-analysis/evidence/timeline.csv` in
the user-supplied `Timer-完整逆向分析交付.zip` (2026-09-25).
The delivery reports comparison against the original Timer JVM instructions,
sample JAR SHA-256 `aba2af9ac9e44f79017cd39f721363a65e75bbd1ad16f83b4735070fc0631548`.

`TimerBalanceTest` replays all 2,000 updates and render calls using the input
schedule from the delivery's `tools/TimerHarness.java`, comparing every saved
sample with exact float equality. The CSV is a fixture, not generated from the
Omix implementation. This does not independently rerun the original JAR or test
Minecraft/GLFW/GPU behavior.
