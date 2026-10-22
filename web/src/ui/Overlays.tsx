import { useEffect, useRef } from "react";
import type { MoveRecord } from "../game/history.ts";

export function ResultOverlay({ won, opponent, moves, walls, seconds, onPlayAgain, onHome }: {
  won: boolean; opponent: string; moves: number; walls: number; seconds: number; onPlayAgain: () => void; onHome: () => void;
}) {
  return (
    <div className="overlay" role="dialog" aria-modal="true" aria-label={won ? "You won" : "You lost"}>
      <div className="modal">
        <h2 className={won ? "won" : "lost"}>{won ? "You won" : "You lost"}</h2>
        <p className="muted">{won ? "You reached the other side." : `${opponent} reached the other side.`}</p>
        <div className="stats">
          <div><strong>{moves}</strong><span>Moves</span></div>
          <div><strong>{walls}</strong><span>Walls used</span></div>
          <div><strong>{Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, "0")}</strong><span>Time</span></div>
        </div>
        <button className="primary" onClick={onPlayAgain}>Play again</button>
        <button className="secondary" onClick={onHome}>Home</button>
      </div>
    </div>
  );
}

export function ConfirmResign({ opponent, onConfirm, onCancel }: { opponent: string; onConfirm: () => void; onCancel: () => void }) {
  return (
    <div className="overlay" role="dialog" aria-modal="true" aria-label="Resign this game?">
      <div className="modal small">
        <h2>Resign this game?</h2>
        <p className="muted">{opponent} will be declared the winner.</p>
        <div className="row">
          <button className="secondary" onClick={onCancel}>Keep playing</button>
          <button className="danger" onClick={onConfirm}>Resign</button>
        </div>
      </div>
    </div>
  );
}

export function HistorySheet({ history, opponent, onClose }: { history: MoveRecord[]; opponent: string; onClose: () => void }) {
  const end = useRef<HTMLLIElement>(null);
  useEffect(() => { end.current?.scrollIntoView({ block: "end" }); }, [history.length]);
  return (
    <div className="overlay sheet-wrap" onClick={onClose}>
      <div className="sheet" role="dialog" aria-modal="true" aria-label="Move history" onClick={(e) => e.stopPropagation()}>
        <div className="grab" />
        <h2>Move history</h2>
        {history.length === 0 ? <p className="muted">No moves yet — make the first one!</p> : (
          <ol>
            {history.map((r, i) => (
              <li key={i} ref={i === history.length - 1 ? end : undefined} className={i === history.length - 1 ? "latest" : ""}>
                <span className={`num ${r.player === 0 ? "mine" : "theirs"}`}>{i + 1}</span>
                {!r.gameEnd && <b className={r.player === 0 ? "mine" : "theirs"}>{r.player === 0 ? "You" : opponent}</b>}
                <span>{r.text}</span>
              </li>
            ))}
          </ol>
        )}
      </div>
    </div>
  );
}
