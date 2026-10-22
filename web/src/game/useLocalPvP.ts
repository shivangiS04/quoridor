import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { deriveStats, type GameController, type WallRef } from "./controller.ts";
import { describe, type MoveRecord } from "./history.ts";
import { type Action, apply, type GameState, getValidMoves, initialState, isWallValid, resign as resignState } from "./rules.ts";
import { play } from "./sound.ts";

/**
 * Two people share one device. Player 1 (red) starts at the bottom and moves first, player 2 (blue) is at the top.
 * Optionally the board turns towards whoever is moving, so two people sitting opposite each other (a tablet on a
 * table) each see their own pawn at the bottom.
 */
export function useLocalPvP(): GameController {
  const [game, setGame] = useState<GameState>(() => initialState(0));
  const [history, setHistory] = useState<MoveRecord[]>([]);
  const [wallHorizontal, setWallHorizontalState] = useState(true);
  const [preview, setPreview] = useState<WallRef | null>(null);
  const [invalid, setInvalid] = useState<(WallRef & { tick: number }) | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [turnBoard, setTurnBoard] = useState(false);
  const [startedAt, setStartedAt] = useState(() => Date.now());
  const [endedAt, setEndedAt] = useState<number | null>(null);
  const gameRef = useRef(game);
  gameRef.current = game;

  const commit = useCallback((action: Action) => {
    const cur = gameRef.current;
    const next = apply(cur, cur.current_turn, action);
    if (!next) return false;
    const rec = describe(cur, next);
    if (rec) setHistory((h) => [...h, rec]);
    gameRef.current = next;
    setGame(next);
    setPreview(null);
    if (next.status === "finished") { setEndedAt(Date.now()); play("win"); }
    else play(action.type === "wall" ? "wall" : "move");
    return true;
  }, []);

  useEffect(() => {
    if (!invalid) return;
    setNotice("Invalid wall placement");
    const t = setTimeout(() => setNotice(null), 2500);
    return () => clearTimeout(t);
  }, [invalid]);

  const playing = game.status === "playing";
  const turn = game.current_turn;
  const validMoves = useMemo(() => (playing ? getValidMoves(game, turn) : []), [game, playing, turn]);

  const flashInvalid = (w: WallRef) => {
    setInvalid((i) => ({ ...w, tick: (i?.tick ?? 0) + 1 }));
    play("invalid");
    navigator.vibrate?.(40);
  };

  const stats = useMemo(() => deriveStats(history, 0, game.players[0].walls_left), [history, game]);
  const name = (seat: number) => `Player ${seat + 1}`;
  return {
    game, me: 0, turnSeat: turn,
    // the board turns towards the player on the move; goal rows and colours stay fixed (red = player 1, blue = player 2)
    flipped: turnBoard && turn === 1,
    selfTitle: name(0), opponentTitle: name(1), opponentName: name(1), nameOf: name,
    history, validMoves, myTurn: playing, finished: !playing, won: game.winner === 0,
    resultText: game.winner === null ? undefined : { title: `${name(game.winner)} wins`, subtitle: "Reached the other side first.", tone: "won" },
    statusText: null,
    flipToggle: { on: turnBoard, toggle: () => setTurnBoard((v) => !v) },
    wallHorizontal,
    setWallHorizontal: (h: boolean) => { setWallHorizontalState(h); setPreview(null); },
    preview, invalid, notice, busy: false,
    wallOwners: stats.wallOwners, wallsUsed: stats.wallsUsed, myTurnsTaken: history.filter((r) => !r.gameEnd).length,
    elapsedSeconds: Math.max(0, Math.floor(((endedAt ?? Date.now()) - startedAt) / 1000)),
    moveTo: (row, col) => { if (playing && validMoves.some((m) => m.row === row && m.col === col)) commit({ type: "move", row, col }); },
    tapGroove: (row, col) => {
      if (!playing) return;
      if (!isWallValid(game, turn, row, col, wallHorizontal)) return flashInvalid({ row, col, horizontal: wallHorizontal });
      if (preview && preview.row === row && preview.col === col && preview.horizontal === wallHorizontal) commit({ type: "wall", row, col, horizontal: wallHorizontal });
      else setPreview({ row, col, horizontal: wallHorizontal });
    },
    dropWall: (row, col, horizontal) => { if (playing && !commit({ type: "wall", row, col, horizontal })) flashInvalid({ row, col, horizontal }); },
    resign: () => {
      const cur = gameRef.current;
      const next = resignState(cur, cur.current_turn);
      const rec = describe(cur, next);
      if (rec) setHistory((h) => [...h, rec]);
      gameRef.current = next; setGame(next); setEndedAt(Date.now()); play("lose");
    },
    playAgain: () => {
      const fresh = initialState(0);
      gameRef.current = fresh; setGame(fresh); setHistory([]); setPreview(null); setInvalid(null); setNotice(null);
      setStartedAt(Date.now()); setEndedAt(null);
    },
  };
}
