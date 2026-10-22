import { useState } from "react";
import type { Difficulty } from "./game/ai.ts";
import { GameScreen } from "./ui/GameScreen.tsx";
import { Home } from "./ui/Home.tsx";

export default function App() {
  const [difficulty, setDifficulty] = useState<Difficulty | null>(null);
  return difficulty
    ? <GameScreen key={difficulty} difficulty={difficulty} onHome={() => setDifficulty(null)} />
    : <Home onPlay={setDifficulty} />;
}
