"""Gymnasium wrappers around the SAME Java physics core shipped in the APK."""
from pathlib import Path
import os
import gymnasium as gym
import jpype
import numpy as np

GENRES = ("JUMPER", "PLATFORMER", "LANES", "JETPACK")

class ArcadeEnv(gym.Env):
    metadata = {"render_modes": [], "render_fps": 15}

    def __init__(self, genre="JUMPER", jar_path=None, render_mode=None):
        if genre not in GENRES or render_mode is not None:
            raise ValueError("Unsupported genre/render mode")
        jar = Path(jar_path or os.environ.get("LOCALPILOT_CORE_JAR", Path(__file__).resolve().parents[2] / "build/localpilot-arcade-core.jar"))
        if not jar.is_file():
            raise FileNotFoundError("Run training/build_core.py or set LOCALPILOT_CORE_JAR")
        jpype.addClassPath(str(jar.resolve()))
        if not jpype.isJVMStarted():
            jpype.startJVM(convertStrings=True)
        cls = jpype.JClass("org.localpilot.android.rl.ArcadeEnv")
        genres = jpype.JClass("org.localpilot.android.rl.ArcadeEnv$Genre")
        self.backend = cls(genres.valueOf(genre))
        self.action_names = tuple(str(x) for x in self.backend.actions())
        self.action_space = gym.spaces.Discrete(len(self.action_names))
        self.observation_space = gym.spaces.Box(-1, 1, (64,), dtype=np.float32)
        self.genre = genre

    def reset(self, *, seed=None, options=None):
        super().reset(seed=seed)
        actual_seed = int(seed) if seed is not None else int(self.np_random.integers(0, 2**63))
        actual_seed = ((actual_seed + 2**63) % 2**64) - 2**63
        observation = np.asarray(self.backend.reset(jpype.JLong(actual_seed)), dtype=np.float32).copy()
        return observation, {"genre": self.genre, "progress": 0.0, "duration_frames": 0}

    def step(self, action):
        if not self.action_space.contains(action):
            raise ValueError("Action outside action_space")
        result = self.backend.step(int(action))
        return (np.asarray(result.observation, dtype=np.float32).copy(), float(result.reward),
                bool(result.terminated), bool(result.truncated),
                {"genre": self.genre, "progress": float(result.progress), "duration_frames": int(result.durationFrames)})

for name, genre in zip(("VerticalJumper", "VerticalPlatformer", "LaneRunner", "Jetpack"), GENRES):
    gym.register(id=f"LocalPilot/{name}-v0", entry_point="localpilot_gymnasium:ArcadeEnv", kwargs={"genre": genre})
