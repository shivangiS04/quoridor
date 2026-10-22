import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { chooseAction, type Difficulty } from "./ai.ts";
import { describe, type MoveRecord } from "./history.ts";
import { type Action, apply, type GameState, getValidMoves, initialState, isWallValid, resign as resignState } from "./rules.ts";
import { play } from "./sound.ts";

export const HUMAN = 0;
const AI = 1;

export interface WallRef { row: number; col: number; horizontal: boolean }

/** A game against the computer: the human is player 0 (bottom) and moves first. */
export function useLocalGame(difficulty: Difficulty) {
  const [game, setGame] = useState<GameState>(() => initialState(0));
  const [history, setHistory] = useState<MoveRecord[]>([]);
  const [thinking, setThinking] = useState(false);
  const [wallHorizontal, setWallHorizontalState] = useState(true);
  const [preview, setPreview] = useState<WallRef | null>(null);
  const [invalid, setInvalid] = useState<(WallRef & { tick: number }) | null>(null);
  const [startedAt, setStartedAt] = useState(() => Date.now());
  const [endedAt, setEndedAt] = useState<number | null>(null);
  const gameRef = useRef(game);
  gameRef.current = game;

  const commit = useCallback((pi: number, action: Action) => {
    const cur = gameRef.current;
    const next = apply(cur, pi, action);
    if (!next) return false;
    const rec = describe(cur, next);
    if (rec) setHistory((h) => [...h, rec]);
    gameRef.current = next;
    setGame(next);
    setPreview(null);
    if (next.status === "finished") {
      setEndedAt(Date.now());
      play(next.winner === HUMAN ? "win" : "lose");
    } else {
      play(action.type === "wall" ? "wall" : "move");
    }
    return true;
  }, []);

  // The computer replies after a short pause so it doesn't feel instant.
  useEffect(() => {
    if (game.status !== "playing" || game.current_turn !== AI) return;
    setThinking(true);
    const t = setTimeout(() => {
      commit(AI, chooseAction(gameRef.current, AI, difficulty));
      setThinking(false);
    }, 500);
    return () => clearTimeout(t);
  }, [game, difficulty, commit]);

  const myTurn = game.status === "playing" && game.current_turn === HUMAN;
  const validMoves = useMemo(() => (myTurn ? getValidMoves(game, HUMAN) : []), [game, myTurn]);

  const flashInvalid = useCallback((w: WallRef) => {
    setInvalid((i) => ({ ...w, tick: (i?.tick ?? 0) + 1 }));
    play("invalid");
    navigator.vibrate?.(40);
  }, []);

  const moveTo = (row: number, col: number) => {
    if (myTurn && validMoves.some((m) => m.row === row && m.col === col)) commit(HUMAN, { type: "move", row, col });
  };

  const setWallHorizontal = (horizontal: boolean) => {
    setWallHorizontalState(horizontal);
    setPreview(null);
  };

  /** Tap a groove: first tap previews, a second tap on the same spot places the wall. */
  const tapGroove = (row: number, col: number) => {
    if (!myTurn) return;
    if (!isWallValid(game, HUMAN, row, col, wallHorizontal)) return flashInvalid({ row, col, horizontal: wallHorizontal });
    if (preview && preview.row === row && preview.col === col && preview.horizontal === wallHorizontal) {
      commit(HUMAN, { type: "wall", row, col, horizontal: wallHorizontal });
    } else {
      setPreview({ row, col, horizontal: wallHorizontal });
    }
  };

  /** A wall dragged from the tray and released over notch (row, col). */
  const dropWall = (row: number, col: number, horizontal: boolean) => {
    if (!myTurn) return;
    if (!commit(HUMAN, { type: "wall", row, col, horizontal })) flashInvalid({ row, col, horizontal });
  };

  const resign = () => {
    const next = resignState(gameRef.current, HUMAN);
    const rec = describe(gameRef.current, next);
    if (rec) setHistory((h) => [...h, rec]);
    gameRef.current = next;
    setGame(next);
    setEndedAt(Date.now());
    play("lose");
  };

  const restart = () => {
    const fresh = initialState(0);
    gameRef.current = fresh;
    setGame(fresh);
    setHistory([]);
    setPreview(null);
    setInvalid(null);
    setThinking(false);
    setStartedAt(Date.now());
    setEndedAt(null);
  };

  /** Who placed each wall this game, so bars can be coloured by owner. */
  const wallOwners = useMemo(() => {
    const m = new Map<string, number>();
    for (const r of history) if (r.wall) m.set(`${r.wall.horizontal ? "h" : "v"}${r.wall.row},${r.wall.col}`, r.player);
    return m;
  }, [history]);

  const wallsUsed = 10 - game.players[HUMAN].walls_left;
  return {
    game, history, thinking, myTurn, validMoves, wallHorizontal, setWallHorizontal, preview, invalid, wallOwners,
    finished: game.status === "finished", won: game.winner === HUMAN, wallsUsed,
    myTurnsTaken: history.filter((r) => r.player === HUMAN && !r.gameEnd).length,
    elapsedSeconds: Math.max(0, Math.floor(((endedAt ?? Date.now()) - startedAt) / 1000)),
    moveTo, tapGroove, dropWall, resign, restart,
  };
}
