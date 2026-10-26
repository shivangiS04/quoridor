import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { type MatchRow, claimTimeout, getRematchRoom, getRoom, requestRematch, submitAction } from "../lib/api.ts";
import { ApiError, client } from "../lib/supabase.ts";
import { deriveStats, type GameController, type WallRef } from "./controller.ts";
import { describe, type MoveRecord } from "./history.ts";
import { type Action, type GameState, getValidMoves, isWallValid, apply } from "./rules.ts";
import { remainingTime, settleClock } from "../shared/quoridor.ts";
import { play } from "./sound.ts";

interface Match { state: GameState; revision: number; updatedAt: number | null }
const toMatch = (r: MatchRow): Match => ({ state: r.state, revision: r.revision, updatedAt: r.updated_at ? Date.parse(r.updated_at) : null });

/**
 * A live game against another player. The server validates and applies every move; this hook sends actions,
 * listens for the match row changing (realtime, with a polling fallback) and exposes the same controller as the
 * computer game. `me` is your seat: seat 1 sees the board turned around.
 */
export function useOnlineGame(
  roomId: string, me: number, initial: MatchRow, opponentName: string, onEnterRoom: (roomId: string) => void,
): GameController {
  const ref = useRef<Match>(toMatch(initial));
  const pending = useRef(false);
  const [prediction, setPrediction] = useState<Match | null>(null);
  const [match, setMatch] = useState<Match>(ref.current);
  const [history, setHistory] = useState<MoveRecord[]>([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [wallHorizontal, setWallHorizontalState] = useState(true);
  const [preview, setPreview] = useState<WallRef | null>(null);
  const [invalid, setInvalid] = useState<(WallRef & { tick: number }) | null>(null);
  const [now, setNow] = useState(Date.now());
  const [startedAt] = useState(Date.now());
  const [endedAt, setEndedAt] = useState<number | null>(initial.state.status === "finished" ? Date.now() : null);
  const [rematchReady, setRematchReady] = useState(false);
  const [rematchBusy, setRematchBusy] = useState(false);

  const applyRow = useCallback((row: MatchRow) => {
    const prev = ref.current;
    if (row.revision <= prev.revision) return;
    // Only describe consecutive revisions; after a gap the history simply skips the missed moves.
    const rec = row.revision === prev.revision + 1 ? describe(prev.state, row.state) : null;
    ref.current = toMatch(row);
    setMatch(ref.current);
    setPrediction(null);
    setNow(Date.now());
    if (rec) setHistory((h) => [...h, rec]);
    setPreview(null);
    if (row.state.status === "finished") {
      setEndedAt(Date.now());
      if (rec) play(row.state.winner === me ? "win" : "lose");
    } else if (rec) {
      play(rec.wall ? "wall" : "move");
    }
  }, [me]);

  const refresh = useCallback(async () => {
    try {
      const info = await getRoom(roomId);
      if (info.match) applyRow(info.match);
    } catch { /* keep what we have; the next tick retries */ }
  }, [roomId, applyRow]);

  // Realtime: the server updates the match row for every accepted action.
  useEffect(() => {
    const c = client();
    const channel = c.channel(`match-${roomId}`)
      .on("postgres_changes", { event: "*", schema: "public", table: "matches", filter: `room_id=eq.${roomId}` }, (payload) => {
        const row = payload.new as (Partial<MatchRow> & { rematch_room_id?: string | null }) | undefined;
        if (row?.rematch_room_id) setRematchReady(true);
        if (row?.state && typeof row.revision === "number") applyRow({ state: row.state, revision: row.revision, updated_at: row.updated_at ?? null });
      })
      .subscribe();
    return () => { void c.removeChannel(channel); };
  }, [roomId, applyRow]);

  // A one-second clock for the cumulative game countdown, and a slow poll as a fallback in case realtime dropped.
  const status = match.state.status;
  useEffect(() => {
    if (status === "finished") return;
    const clock = setInterval(() => setNow(Date.now()), 1000);
    const poll = setInterval(() => { void refresh(); }, 3000);
    return () => { clearInterval(clock); clearInterval(poll); };
  }, [status, refresh]);

  // After the game: has the other player already asked for a rematch? (realtime tells us; this is the safety net)
  useEffect(() => {
    if (status !== "finished" || rematchReady) return;
    let alive = true;
    const look = () => getRematchRoom(roomId).then((id) => { if (alive && id) setRematchReady(true); }).catch(() => {});
    void look();
    const t = setInterval(look, 3000);
    return () => { alive = false; clearInterval(t); };
  }, [status, rematchReady, roomId]);

  useEffect(() => {
    if (!notice) return;
    const t = setTimeout(() => setNotice(null), 2500);
    return () => clearTimeout(t);
  }, [notice]);

  const displayed = prediction ?? match;
  const game = displayed.state;
  const myTurn = game.status === "playing" && game.current_turn === me;
  const validMoves = useMemo(() => (myTurn && !busy ? getValidMoves(game, me) : []), [game, myTurn, busy, me]);

  const submit = useCallback(async (action: Action | { type: "resign" }) => {
    if (pending.current || ref.current.state.status !== "playing") return;
    pending.current = true;
    const base = ref.current;
    const at = Date.now();
    // Predict the board immediately with the same rules; only confirmed rows advance revision/history/results.
    if (action.type !== "resign") {
      const predicted = apply(settleClock(base.state, base.updatedAt ?? at, at), me, action);
      if (!predicted) { pending.current = false; return; }
      setPrediction({ state: predicted, revision: base.revision, updatedAt: at });
      setPreview(null);
      setNow(at);
    }
    setBusy(true);
    try {
      const result = await submitAction(roomId, base.revision, action);
      applyRow(result.match);
    } catch (e) {
      // A lost response may still have committed. Resync for every error before rolling back the prediction.
      await refresh();
      if (ref.current.revision === base.revision) {
        setNotice(e instanceof ApiError && e.isStale ? "The board changed — try again."
          : e instanceof ApiError ? e.message : "Something went wrong. Try again.");
      }
    } finally {
      pending.current = false;
      setPrediction(null);
      setBusy(false);
    }
  }, [roomId, refresh, me, applyRow]);

  const flashInvalid = (w: WallRef) => {
    setInvalid((i) => ({ ...w, tick: (i?.tick ?? 0) + 1 }));
    setNotice("Invalid wall placement");
    play("invalid");
    navigator.vibrate?.(40);
  };

  const moveTo = (row: number, col: number) => {
    if (myTurn && !busy && validMoves.some((m) => m.row === row && m.col === col)) void submit({ type: "move", row, col });
  };
  const setWallHorizontal = (h: boolean) => { setWallHorizontalState(h); setPreview(null); };
  const tapGroove = (row: number, col: number) => {
    if (!myTurn || busy) return;
    if (!isWallValid(game, me, row, col, wallHorizontal)) return flashInvalid({ row, col, horizontal: wallHorizontal });
    if (preview && preview.row === row && preview.col === col && preview.horizontal === wallHorizontal) {
      void submit({ type: "wall", row, col, horizontal: wallHorizontal });
    } else {
      setPreview({ row, col, horizontal: wallHorizontal });
    }
  };
  const dropWall = (row: number, col: number, horizontal: boolean) => {
    if (!myTurn || busy) return;
    if (!isWallValid(game, me, row, col, horizontal)) return flashInvalid({ row, col, horizontal });
    void submit({ type: "wall", row, col, horizontal });
  };
  const resign = () => { void submit({ type: "resign" }); };

  const secondsLeft = game.status === "playing" && displayed.updatedAt !== null
    ? Math.ceil(remainingTime(game, game.current_turn, displayed.updatedAt, now) / 1000) : null;
  const claim = () => {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    claimTimeout(roomId).then((result) => applyRow(result.match)).catch(async (e) => {
      await refresh();
      setNotice(e instanceof ApiError ? e.message : "Could not claim the win.");
    }).finally(() => { pending.current = false; setBusy(false); });
  };

  const goRematch = () => {
    setRematchBusy(true);
    requestRematch(roomId)
      .then((newRoom) => onEnterRoom(newRoom))
      .catch((e) => { setNotice(e instanceof ApiError ? e.message : "Could not start a rematch."); setRematchBusy(false); });
  };

  const stats = useMemo(() => deriveStats(history, me, game.players[me].walls_left), [history, game, me]);
  return {
    game, me, flipped: me === 1, turnSeat: game.current_turn,
    nameOf: (seat: number) => (seat === me ? "You" : opponentName),
    rematch: { ready: rematchReady, busy: rematchBusy, go: goRematch },
    opponentTitle: opponentName, opponentName,
    history, validMoves, myTurn, finished: match.state.status === "finished", won: match.state.winner === me,
    statusText: !myTurn && game.status === "playing" ? `Waiting for ${opponentName}…` : null,
    wallHorizontal, setWallHorizontal, preview, invalid, notice, busy,
    ...stats,
    elapsedSeconds: Math.max(0, Math.floor(((endedAt ?? now) - startedAt) / 1000)),
    moveTo, tapGroove, dropWall, resign,
    timer: { secondsLeft, canClaim: !myTurn && secondsLeft === 0, claim },
  };
}
