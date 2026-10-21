"""Actual Gymnasium contract checker, all four native-backed environments."""
import gymnasium as gym
from gymnasium.utils.env_checker import check_env
import localpilot_gymnasium
from pathlib import Path

for name in ("VerticalJumper", "VerticalPlatformer", "LaneRunner", "Jetpack"):
    env = gym.make(f"LocalPilot/{name}-v0", jar_path=str(Path(__file__).resolve().parent / "build/localpilot-arcade-core.jar"))
    check_env(env.unwrapped, skip_render_check=True)
    print(f"PASS Gymnasium check_env: {name}")
    env.close()
