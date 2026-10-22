import { useEffect, useMemo, useRef, useState } from "react";
import { DIFFICULTY_LABEL, type Difficulty } from "../game/ai.ts";
import { metrics, nearestNotch, SIZE, wallBar } from "../game/geometry.ts";
import { isWallValid } from "../game/rules.ts";
import { setSoundEnabled, soundEnabled } from "../game/sound.ts";
import { HUMAN, useLocalGame } from "../game/useLocalGame.ts";
import { Board, type DragTarget } from "./Board.tsx";
import { ConfirmResign, HistorySheet, ResultOverlay } from "./Overlays.tsx";
import { PlayerCard } from "./PlayerCard.tsx";
import { WallTray } from "./WallTray.tsx";

/** How far above the fingertip a dragged wall is held, so it stays visible. */
const LIFT_PX = 48;
const metricsOnce = metrics();

interface Drag { horizontal: boolean; x: number; y: number }

export function GameScreen({ difficulty, onHome }: { difficulty: Difficulty; onHome: () => void }) {
  const g = useLocalGame(difficulty);
  const svgRef = useRef<SVGSVGElement>(null);
  const [drag, setDrag] = useState<Drag | null>(null);
  const [showHistory, setShowHistory] = useState(false);
  const [confirmResign, setConfirmResign] = useState(false);
  const [sound, setSound] = useState(soundEnabled);
  const [message, setMessage] = useState<string | null>(null);
  const opponent = "Computer";

  // Illegal wall placements show a short message that fades after a moment.
  useEffect(() => {
    if (!g.invalid) return;
    setMessage("Invalid wall placement");
    const t = setTimeout(() => setMessage(null), 2500);
    return () => clearTimeout(t);
  }, [g.invalid]);

  // A wall in the hand is dropped if the game ends or the turn passes while dragging.
  useEffect(() => { if (!g.myTurn) setDrag(null); }, [g.myTurn]);

  /** Where the carried wall would land (null while it is not over the board). */
  const target: DragTarget | null = useMemo(() => {
    const rect = svgRef.current?.getBoundingClientRect();
    if (!drag || !rect) return null;
    const k = SIZE / rect.width;
    const ax = (drag.x - rect.left) * k, ay = (drag.y - LIFT_PX - rect.top) * k;
    if (ax < 0 || ay < 0 || ax > SIZE || ay > SIZE) return null;
    const { row, col } = nearestNotch(ax, ay, metricsOnce);
    return { row, col, horizontal: drag.horizontal, valid: isWallValid(g.game, HUMAN, row, col, drag.horizontal) };
  }, [drag, g.game]);

  const lastWall = g.history.at(-1)?.wall ?? null;
  const canPlaceWall = g.myTurn && g.game.players[HUMAN].walls_left > 0;
  const lastText = g.thinking ? `${opponent} is thinking…`
    : g.history.length === 0 ? "No moves yet"
    : g.history.at(-1)!.gameEnd ? "Game over"
    : `${g.history.length}. ${g.history.at(-1)!.player === HUMAN ? "You" : opponent}: ${g.history.at(-1)!.text}`;

  const rect = svgRef.current?.getBoundingClientRect();
  const heldSize = rect && drag ? (() => { const b = wallBar(drag.horizontal, metricsOnce); const k = rect.width / SIZE; return { w: b.w * k, h: b.h * k }; })() : null;

  return (
    <div className="screen">
      <header className="topbar">
        <button className="icon" aria-label="Back to home" onClick={onHome}>←</button>
        <button className="resign" disabled={g.finished} onClick={() => setConfirmResign(true)}>⚑ Resign</button>
        <span className="spacer" />
        <button className="icon" aria-label={sound ? "Mute" : "Unmute"} onClick={() => { setSound(!sound); setSoundEnabled(!sound); }}>
          {sound ? "🔊" : "🔇"}
        </button>
      </header>

      <div className="cards">
        <PlayerCard name="You" tone="mine" wallsLeft={g.game.players[HUMAN].walls_left} active={g.myTurn} />
        <PlayerCard name={`${opponent} · ${DIFFICULTY_LABEL[difficulty]}`} tone="theirs" wallsLeft={g.game.players[1].walls_left}
          active={!g.myTurn && !g.finished} />
      </div>

      <button className="strip" onClick={() => setShowHistory(true)} aria-label={`${lastText}. Open move history`}>{lastText}</button>

      <div className="board-area">
        <Board game={g.game} me={HUMAN} validMoves={g.validMoves} owners={g.wallOwners} lastWall={lastWall}
          preview={g.preview} target={target} invalid={g.invalid} svgRef={svgRef}
          onCell={g.moveTo} onGroove={g.tapGroove} />
      </div>

      <div className="slot">{message && <span className="warn">⚠ {message}</span>}</div>

      <WallTray
        selectedHorizontal={g.wallHorizontal}
        enabled={canPlaceWall}
        onSelect={g.setWallHorizontal}
        onDragStart={(horizontal, x, y) => setDrag({ horizontal, x, y })}
        onDragMove={(x, y) => setDrag((d) => (d ? { ...d, x, y } : d))}
        onDragEnd={() => { if (target) g.dropWall(target.row, target.col, target.horizontal); setDrag(null); }}
        onDragCancel={() => setDrag(null)}
      />

      {drag && heldSize && (
        <div className={`held${target && !target.valid ? " bad" : ""}`}
          style={{ left: drag.x - heldSize.w / 2, top: drag.y - LIFT_PX - heldSize.h / 2, width: heldSize.w, height: heldSize.h }} />
      )}

      {g.finished && (
        <ResultOverlay won={g.won} opponent={opponent} moves={g.myTurnsTaken} walls={g.wallsUsed} seconds={g.elapsedSeconds}
          onPlayAgain={g.restart} onHome={onHome} />
      )}
      {confirmResign && <ConfirmResign opponent={opponent} onCancel={() => setConfirmResign(false)}
        onConfirm={() => { setConfirmResign(false); g.resign(); }} />}
      {showHistory && <HistorySheet history={g.history} opponent={opponent} onClose={() => setShowHistory(false)} />}
    </div>
  );
}
