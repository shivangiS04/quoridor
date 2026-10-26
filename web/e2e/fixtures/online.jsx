// Development-only harness: the browser test replaces the API/Realtime modules, never the game hook or rules.
import React from "react";
import { createRoot } from "react-dom/client";
import { useOnlineGame } from "../../src/game/useOnlineGame.ts";
import { initialState, applyTimedAction } from "../../src/shared/quoridor.ts";
import { GameScreen } from "../../src/ui/GameScreen.tsx";
import "../../src/styles.css";

const seat = Number(new URLSearchParams(location.search).get("seat") ?? 0);
window.driver = {
  row: { state: initialState(0), revision: 0, updated_at: new Date().toISOString() },
  requests: [], listeners: new Set(), reads: 0, key: 0,
  submit(revision, action) {
    return new Promise((resolve, reject) => this.requests.push({ revision, action, resolve, reject }));
  },
  accept(index = this.requests.length - 1, notify = false) {
    const req = this.requests[index];
    const now = Date.now();
    this.row = {
      state: applyTimedAction(this.row.state, seat, req.action, Date.parse(this.row.updated_at), now),
      revision: req.revision + 1, updated_at: new Date(now).toISOString(),
    };
    if (notify) this.broadcast(this.row);
    req.resolve({ ok: true, match: structuredClone(this.row) });
    return this.row;
  },
  broadcast(row) {
    this.row = structuredClone(row);
    for (const listener of this.listeners) listener({ new: row });
  },
  reset(row) {
    this.row = row ?? { state: initialState(0), revision: 0, updated_at: new Date().toISOString() };
    this.requests = []; this.reads = 0; this.key++;
    render();
  },
};
function App() {
  const game = useOnlineGame("test-room", seat, structuredClone(window.driver.row), "Opponent", () => {});
  window.game = game;
  return <GameScreen g={game} onHome={() => {}} />;
}
const root = createRoot(document.getElementById("root"));
function render() { root.render(<App key={window.driver.key} />); }
render();
