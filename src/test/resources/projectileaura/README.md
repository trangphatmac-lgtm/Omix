# ProjectileAura regression fixtures

Source: user-supplied `ProjectileAura-完整逆向分析交付.zip`, directory
`projectileaura-analysis/evidence/`. `provenance.json` identifies the original sample.

- `native-golden.tsv`: 155 recorded original native state/effect cases.
- `math-golden.tsv`: 326 recorded original Java bytecode numerical/cooldown cases.
- `selection-golden.tsv`: 54 recorded original native selection/visibility cases.

The three differential drivers are adapted from the delivery by adding a package and
renaming `ProjectileAuraReadable` to the production `ProjectileAuraEngine`.
JUnit's `ProjectileAuraDifferentialTest` invokes them against these immutable goldens.
Native fixtures stub external policy/selection/aim helpers; they do not establish
Minecraft integration or server behavior. No native payload is loaded or shipped.

`item-policy-evidence.json` records an additional static/decryption check of the
supplied `i1ilil1iiiii.java.txt`: the external exclusion predicate rejects wind
charges and item hover text containing `wind charge` or `风弹`, not all named items.
It is provenance for the name constants, not another native execution oracle.
