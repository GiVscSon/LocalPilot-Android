"""Continue an Android checkpoint with the identical Java trainer on a PC."""
import argparse
from pathlib import Path
import gymnasium as gym
import jpype
import localpilot_gymnasium

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--genre", choices=localpilot_gymnasium.GENRES, default="JUMPER")
    parser.add_argument("--steps", type=int, default=10000)
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--evaluate", action="store_true")
    args = parser.parse_args()
    if args.steps < 0:
        parser.error("steps must be non-negative")
    env = localpilot_gymnasium.ArcadeEnv(args.genre)
    trainer_type = jpype.JClass("org.localpilot.android.rl.DqnTrainer")
    genre_type = jpype.JClass("org.localpilot.android.rl.ArcadeEnv$Genre")
    file_type = jpype.JClass("java.io.File")
    checkpoint = file_type(str(args.checkpoint.resolve()))
    trainer = trainer_type.loadRecovering(checkpoint, genre_type.valueOf(args.genre), 20261021)
    try:
        for _ in range(args.steps):
            trainer.step()
            if trainer.steps % 1000 == 0:
                print(f"steps={trainer.steps} updates={trainer.updates} episodes={trainer.episodes} loss={trainer.loss:.5f}", flush=True)
            if trainer.steps % 10000 == 0:
                trainer.save(checkpoint)
    except KeyboardInterrupt:
        print("Paused; saving checkpoint")
    trainer.save(checkpoint)
    if args.evaluate:
        print(trainer.evaluate(50, 900000, jpype.JProxy("java.util.function.BooleanSupplier", dict(getAsBoolean=lambda: False))))
    env.close()

if __name__ == "__main__":
    main()
