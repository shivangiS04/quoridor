import { useEffect, useRef } from "react";
import type { MoveRecord } from "../game/history.ts";

interface ResultProps {
  title: string;
  subtitle: string;
  tone: "won" | "lost";
  moves: number;
  walls: number;
  seconds: number;
  onPlayAgain?: () => void;
  /** Online: ask for a rematch. `rematchReady` means the opponent already did. */
  onRematch?: () => void;
  rematchReady?: boolean;
  rematchBusy?: boolean;
  opponent: string;
  onHome: () => void;
}

export function ResultOverlay(p: ResultProps) {
  return (
    <div className="overlay" role="dialog" aria-modal="true" aria-label={p.title}>
      <div className="modal">
        <h2 className={p.tone}>{p.title}</h2>
        <p className="muted">{p.subtitle}</p>
        <div className="stats">
          <div><strong>{p.moves}</strong><span>Moves</span></div>
          <div><strong>{p.walls}</strong><span>Walls used</span></div>
          <div><strong>{Math.floor(p.seconds / 60)}:{String(p.seconds % 60).padStart(2, "0")}</strong><span>Time</span></div>
        </div>
        {p.onPlayAgain && <button className="primary" onClick={p.onPlayAgain}>Play again</button>}
        {p.onRematch && (
          <>
            {p.rematchReady && <p className="ok small">{p.opponent} wants a rematch!</p>}
            <button className="primary" disabled={p.rematchBusy} onClick={p.onRematch}>
              {p.rematchBusy ? "Starting…" : p.rematchReady ? "Accept rematch" : "Rematch"}
            </button>
          </>
        )}
        <button className="secondary" onClick={p.onHome}>Home</button>
      </div>
    </div>
  );
}

export function ConfirmResign({ who, onConfirm, onCancel }: { who: string; onConfirm: () => void; onCancel: () => void }) {
  return (
    <div className="overlay" role="dialog" aria-modal="true" aria-label="Resign this game?">
      <div className="modal small">
        <h2>Resign this game?</h2>
        <p className="muted">{who} will be declared the winner.</p>
        <div className="row">
          <button className="secondary" onClick={onCancel}>Keep playing</button>
          <button className="danger" onClick={onConfirm}>Resign</button>
        </div>
      </div>
    </div>
  );
}

/** `nameOf(seat)` names a player; `isMine(seat)` decides the colour (red for you, blue for the other). */
export function HistorySheet({ history, nameOf, isMine, onClose }: {
  history: MoveRecord[]; nameOf: (seat: number) => string; isMine: (seat: number) => boolean; onClose: () => void;
}) {
  const end = useRef<HTMLLIElement>(null);
  useEffect(() => { end.current?.scrollIntoView({ block: "end" }); }, [history.length]);
  return (
    <div className="overlay sheet-wrap" onClick={onClose}>
      <div className="sheet" role="dialog" aria-modal="true" aria-label="Move history" onClick={(e) => e.stopPropagation()}>
        <div className="grab" />
        <h2>Move history</h2>
        {history.length === 0 ? <p className="muted">No moves yet — make the first one!</p> : (
          <ol>
            {history.map((r, i) => {
              const tone = isMine(r.player) ? "mine" : "theirs";
              return (
                <li key={i} ref={i === history.length - 1 ? end : undefined} className={i === history.length - 1 ? "latest" : ""}>
                  <span className={`num ${tone}`}>{i + 1}</span>
                  {!r.gameEnd && <b className={tone}>{nameOf(r.player)}</b>}
                  <span>{r.text}</span>
                </li>
              );
            })}
          </ol>
        )}
      </div>
    </div>
  );
}
