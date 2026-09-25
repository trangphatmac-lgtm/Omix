# GrimPlus native reference fixtures

Copied without execution from the user-supplied `NoFall-完整逆向分析交付.zip`,
`nofall-analysis/evidence/`. The analyzed sample is
`EdNaven_Fabric-3.4-LATEST-no-sayori.jar`, SHA-256
`aba2af9ac9e44f79017cd39f721363a65e75bbd1ad16f83b4735070fc0631548`.

- `differential-input.tsv` / `differential-native.tsv`: 700 native input/output
  pairs, including 592 independent cases and 108 timeline steps.
- `timeline.json`: the same 108 timeline steps, replayed without restoring state
  between callbacks so cancellation, input consumption and repeated triggers are checked.

`GrimPlusStateTest` runs the production state machine with Omix event objects and
compares all five persistent state fields plus ordered effects against these results.
The 392 time-query combinations are also checked against the documented query
semantics; tests do not execute the original JVM method or native library.
These fixtures do not prove live server behavior.
