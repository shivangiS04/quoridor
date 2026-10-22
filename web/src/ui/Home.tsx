import { DIFFICULTIES, DIFFICULTY_LABEL, type Difficulty } from "../game/ai.ts";

export function Home({ onPlay }: { onPlay: (d: Difficulty) => void }) {
  return (
    <div className="home">
      <div className="home-inner">
        <p className="dots"><span className="mine" /><span className="theirs" /></p>
        <h1>Quoridor</h1>
        <p className="muted">Race to the other side. Block your opponent with walls.</p>

        <h3>Play the computer</h3>
        <div className="levels">
          {DIFFICULTIES.map((d) => (
            <button key={d} className="level" onClick={() => onPlay(d)}>{DIFFICULTY_LABEL[d]}</button>
          ))}
        </div>

        <p className="soon">Online rooms, sign-in and the leaderboard are coming to the web version soon.<br />
          They are already in the Android app.</p>

        <footer>
          <a href="/privacy">Privacy</a> · <a href="https://github.com/shivangiS04/quoridor">Source</a>
        </footer>
      </div>
    </div>
  );
}
