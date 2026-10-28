# LocalPilot RL: byte-exact parity gate

The on-device DQN simulation and the PC trainer **reuse the same pure Java
`org.localpilot.android.rl` sources**. Python/JPype is a transport layer, not a
second independent simulator.

## Run (Windows PowerShell or Ubuntu)

From the repository root, with a **full JDK 17 or newer** and Python:

```sh
python -m pip install jpype1
python training/verify_bit_parity.py
java tests/RlVerify.java
```

The Python check builds `training/build/localpilot-arcade-core.jar` using
`javac --release 17`. It compares SHA-256 over **raw IEEE-754 binary32 bits**
from an independent Java process and a Python/JPype replay of all 64 observation
slots, action, reward, progress, terminal/truncated flags and duration for 64
deterministically selected actions per genre. A second gate runs 1040
deterministic training steps per genre, crossing the replay warmup threshold
and updating Double-DQN/Adam weights. It compares the entire binary
`state.bin` payload (network, optimizer, replay, RNG, environment and
counters) across separate Java and JPype processes. The Python side also
checks checkpoint byte order, manifest, entry list and SHA256SUMS integrity.
It does not touch a user's checkpoint.

`tests/RlVerify.java` compiles with strict `--release 17`, runs the existing
DQN regressions and prints the same deterministic per-genre trace hashes.
If two outputs differ, stop: do not import, train or deploy that model.

## Version contract (do not silently change)

- Observation version: `arcade-objects-v1`, 64 finite float32 values in [-1, 1].
- World: 360 × 640; 60 simulation ticks/s, 4 ticks/action, up to 2000 actions.
- Genres currently implemented: `JUMPER`, `PLATFORMER`, `LANES`, `JETPACK`.
  The real-game `PADDLE`, `FLAPPY` and `TRAFFIC` Android vision profiles are
  **not** standalone RL simulation genres in this checkpoint schema.
- Network: 64→64→64→number of genre actions; Double DQN, Adam, Huber loss.
- v1 checkpoint ZIP contains **three** entries, not two:
  `manifest.json`, `state.bin` and `SHA256SUMS`. The third entry guards
  against corruption; do not remove it to match an external prose specification.
- `.lpweb` (v3 web demonstrations) is a separate format, not a DQN replay
  buffer or a DQN checkpoint. Importing it into RL requires an explicit,
  reviewed, versioned observation/action mapping.
- Existing simulator reward scales are part of the training dynamics. Changing
  them requires a separately versioned environment and fresh evaluation against
  held-out seeds. Higher synthetic reward does not prove superior performance
  in Yandex Market games.

## Limits

This checks deterministic cross-process Java and JPype replay with the **same
compiled core**, not visual interpretation, physics correspondence with third-
party games, actual Android ARM execution, real on-device input, or superiority
to a human. Android acceptance and physical device verification remain separate
gates. No modifications to the Android gesture executor, input permits or STOP
semantics are authorized by a passing RL parity test.
