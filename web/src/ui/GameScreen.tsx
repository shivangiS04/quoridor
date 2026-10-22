import { useEffect, useMemo, useRef, useState } from "react";
import type { GameController } from "../game/controller.ts";
import { flipNotch, metrics, nearestNotch, SIZE, wallBar } from "../game/geometry.ts";
import { isWallValid } from "../game/rules.ts";
import { setSoundEnabled, soundEnabled } from "../game/sound.ts";
import { Board, type DragTarget } from "./Board.tsx";
import { ConfirmResign, HistorySheet, ResultOverlay } from "./Overlays.tsx";
import { PlayerCard } from "./PlayerCard.tsx";
import { WallTray } from "./WallTray.tsx";

/** How far above the fingertip a dragged wall is held, so it stays visible. */
const LIFT_PX = 48;
const metricsOnce = metrics();

interface Drag { horizontal: boolean; x: number; y: number }

export function GameScreen({ g, onHome }: { g: GameController; onHome: () => void }) {
  const svgRef = useRef<SVGSVGElement>(null);
  const [drag, setDrag] = useState<Drag | null>(null);
  const [showHistory, setShowHistory] = useState(false);
  const [confirmResign, setConfirmResign] = useState(false);
  const [sound, setSound] = useState(soundEnabled);
  const opponent = g.opponentName;
  const me = g.me;

  // A wall in the hand is dropped if the game ends or the turn passes while dragging.
  useEffect(() => { if (!g.myTurn) setDrag(null); }, [g.myTurn]);

  /** Where the carried wall would land (null while it is not over the board). */
  const target: DragTarget | null = useMemo(() => {
    const rect = svgRef.current?.getBoundingClientRect();
    if (!drag || !rect) return null;
    const k = SIZE / rect.width;
    const ax = (drag.x - rect.left) * k, ay = (drag.y - LIFT_PX - rect.top) * k;
    if (ax < 0 || ay < 0 || ax > SIZE || ay > SIZE) return null;
    // nearestNotch works in what is drawn; the rules work in logical coordinates (they differ when the board is turned).
    const view = nearestNotch(ax, ay, metricsOnce);
    const row = flipNotch(view.row, g.flipped), col = flipNotch(view.col, g.flipped);
    return { row, col, horizontal: drag.horizontal, valid: isWallValid(g.game, me, row, col, drag.horizontal) };
  }, [drag, g.game, g.flipped, me]);

  const lastWall = g.history.at(-1)?.wall ?? null;
  const canPlaceWall = g.myTurn && !g.busy && g.game.players[me].walls_left > 0;
  const lastText = g.statusText
    ?? (g.history.length === 0 ? "No moves yet"
      : g.history.at(-1)!.gameEnd ? "Game over"
      : `${g.history.length}. ${g.history.at(-1)!.player === me ? "You" : opponent}: ${g.history.at(-1)!.text}`);

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
        <PlayerCard name="You" tone="mine" wallsLeft={g.game.players[me].walls_left} active={g.myTurn} />
        <PlayerCard name={g.opponentTitle} tone="theirs" wallsLeft={g.game.players[1 - me].walls_left}
          active={!g.myTurn && !g.finished} />
      </div>

      <button className="strip" onClick={() => setShowHistory(true)} aria-label={`${lastText}. Open move history`}>{lastText}</button>

      <div className="board-area">
        <Board game={g.game} me={me} flipped={g.flipped} validMoves={g.validMoves} owners={g.wallOwners} lastWall={lastWall}
          preview={g.preview} target={target} invalid={g.invalid} svgRef={svgRef}
          onCell={g.moveTo} onGroove={g.tapGroove} />
      </div>

      <div className="slot">
        {g.notice ? <span className="warn">⚠ {g.notice}</span>
          : g.timer?.canClaim ? <button className="claim" disabled={g.busy} onClick={g.timer.claim}>{opponent} timed out — claim win</button>
          : g.timer?.secondsLeft != null && !g.finished ? (
            <span className="muted small">{g.myTurn ? "Your" : `${opponent}'s`} time left: {Math.floor(g.timer.secondsLeft / 60)}:{String(g.timer.secondsLeft % 60).padStart(2, "0")}</span>
          ) : null}
      </div>

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
          onPlayAgain={g.playAgain} onHome={onHome} />
      )}
      {confirmResign && <ConfirmResign opponent={opponent} onCancel={() => setConfirmResign(false)}
        onConfirm={() => { setConfirmResign(false); g.resign(); }} />}
      {showHistory && <HistorySheet history={g.history} opponent={opponent} onClose={() => setShowHistory(false)} />}
    </div>
  );
}
