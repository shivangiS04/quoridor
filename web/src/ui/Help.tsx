import { type ReactNode } from "react";
import { GAME_TIME_MS } from "../game/rules.ts";
import { onlineConfigured } from "../lib/supabase.ts";

// ---------------------------------------------------------------------------------------------
// Small pictures: a 5×5 board with pawns, highlighted cells and walls. Coordinates are like the real game:
// cells (row, col), wall notches (row, col) = the corner between four cells, a wall is two cells long.
// ---------------------------------------------------------------------------------------------
interface Pic {
  pawns?: { r: number; c: number; tone: "mine" | "theirs" }[];
  hints?: { r: number; c: number }[];
  walls?: { r: number; c: number; h: boolean; tone?: "mine" | "theirs" }[];
  goals?: { row: number; tone: "mine" | "theirs" }[];
  arrow?: { from: [number, number]; to: [number, number] };
  alt: string;
}

const N = 5, S = 250, GAP = 0.14; // gutter fraction, same as the real board
const gap = (S / (N + (N - 1) * GAP)) * GAP;
const cell = (S - gap * (N - 1)) / N;
const stride = cell + gap;
const centre = (r: number, c: number) => ({ x: c * stride + cell / 2, y: r * stride + cell / 2 });

function Diagram({ pawns = [], hints = [], walls = [], goals = [], arrow, alt }: Pic) {
  return (
    <svg className="diagram" viewBox={`-6 -6 ${S + 12} ${S + 12}`} role="img" aria-label={alt}>
      <defs>
        <marker id="arrowhead" markerWidth="8" markerHeight="8" refX="5" refY="4" orient="auto">
          <path d="M0,0 L8,4 L0,8 z" fill="currentColor" />
        </marker>
      </defs>
      {Array.from({ length: N * N }, (_, i) => {
        const r = Math.floor(i / N), c = i % N, goal = goals.find((g) => g.row === r);
        return (
          <g key={i}>
            <rect className="tile" x={c * stride} y={r * stride} width={cell} height={cell} rx={8} />
            {goal && <rect className={`goal ${goal.tone}`} x={c * stride} y={r * stride} width={cell} height={cell} rx={8} />}
          </g>
        );
      })}
      {hints.map((h) => <rect key={`h${h.r}${h.c}`} className="hint" x={h.c * stride + 2} y={h.r * stride + 2} width={cell - 4} height={cell - 4} rx={8} />)}
      {walls.map((w, i) => {
        const long = 2 * cell + gap, thick = gap * 1.6, off = (thick - gap) / 2;
        const x = w.h ? w.c * stride : w.c * stride + cell - off, y = w.h ? w.r * stride + cell - off : w.r * stride;
        return <rect key={i} className={`wall ${w.tone ?? "mine"}`} x={x} y={y} width={w.h ? long : thick} height={w.h ? thick : long} rx={thick / 2} />;
      })}
      {pawns.map((p, i) => { const { x, y } = centre(p.r, p.c); return <circle key={i} className={`pawncirc ${p.tone}`} cx={x} cy={y} r={cell * 0.3} />; })}
      {arrow && (() => {
        const a = centre(...arrow.from), b = centre(...arrow.to);
        return <line x1={a.x} y1={a.y - 18} x2={b.x} y2={b.y + 22} stroke="currentColor" strokeWidth={5} strokeLinecap="round" markerEnd="url(#arrowhead)" opacity={0.9} />;
      })()}
    </svg>
  );
}

function Section({ title, children, open = false }: { title: string; children: ReactNode; open?: boolean }) {
  return (
    <details className="help-sec" open={open}>
      <summary>{title}</summary>
      <div className="help-body">{children}</div>
    </details>
  );
}

const Dot = ({ tone }: { tone: "mine" | "theirs" }) => <span className={`dot-inline ${tone}`} aria-hidden="true" />;

/** The full guide: goal, rules, what every button means, and every mode. */
export function HelpContent() {
  return (
    <div className="help">
      <Section title="The goal" open>
        <p>You and your opponent each have one pawn. <b>Be the first to reach the opposite side of the board.</b></p>
        <Diagram alt="A board where the red pawn at the bottom heads for the top row, and the blue pawn at the top heads for the bottom row"
          pawns={[{ r: 4, c: 2, tone: "mine" }, { r: 0, c: 2, tone: "theirs" }]}
          goals={[{ row: 0, tone: "mine" }, { row: 4, tone: "theirs" }]} arrow={{ from: [4, 2], to: [1, 2] }} />
        <p className="cap">You are <Dot tone="mine" /> <b>red</b> and start at the bottom. Your target is the <b>top row</b> (tinted red). Your opponent is <Dot tone="theirs" /> <b>blue</b>.</p>
      </Section>

      <Section title="On your turn: do ONE of these" open>
        <h4>1. Move your pawn one square</h4>
        <p>Up, down, left or right (never diagonally). The squares you can reach are <b>outlined in white</b>. Tap one to move there.</p>
        <Diagram alt="A red pawn with four white outlined squares around it showing where it can move"
          pawns={[{ r: 3, c: 2, tone: "mine" }, { r: 0, c: 2, tone: "theirs" }]} hints={[{ r: 2, c: 2 }, { r: 4, c: 2 }, { r: 3, c: 1 }, { r: 3, c: 3 }]} />
        <h4>2. Place a wall</h4>
        <p>A wall is <b>two squares long</b> and slots into the grooves between squares. Nobody can walk through it, so use walls to slow your opponent down. Each player has <b>10 walls</b> for the whole game, and walls <b>can't be moved or removed</b>.</p>
        <Diagram alt="A red wall lying across the board in front of the red pawn, so the pawn can no longer step straight up"
          pawns={[{ r: 3, c: 2, tone: "mine" }, { r: 0, c: 2, tone: "theirs" }]} walls={[{ r: 2, c: 1, h: true }]} hints={[{ r: 4, c: 2 }, { r: 3, c: 1 }, { r: 3, c: 3 }]} />
        <p className="cap">The wall blocks the way up, so this pawn can now only go sideways or back.</p>
      </Section>

      <Section title="Wall rules (why a wall is sometimes refused)">
        <ul>
          <li>Walls can't <b>overlap</b> or <b>cross</b> another wall.</li>
          <li>You may <b>never completely block</b> a pawn from its goal. There must always be at least one open route for <i>both</i> players.</li>
          <li>You can't place a wall once your 10 are used up.</li>
        </ul>
        <p>If a wall isn't allowed, you'll see a short <b>"Invalid wall placement"</b> message and it isn't placed. Just try another spot.</p>
      </Section>

      <Section title="Jumping over the other pawn">
        <p>If the pawns are side by side (and no wall is between you), you <b>jump straight over</b> your opponent to the square behind them.</p>
        <Diagram alt="A red pawn directly below a blue pawn, with the square above the blue pawn outlined as the landing spot"
          pawns={[{ r: 3, c: 2, tone: "mine" }, { r: 2, c: 2, tone: "theirs" }]} hints={[{ r: 1, c: 2 }, { r: 3, c: 1 }, { r: 3, c: 3 }, { r: 4, c: 2 }]} />
        <p>If that square is blocked by a wall or the edge of the board, you <b>step diagonally</b> to either side of your opponent instead.</p>
        <Diagram alt="A red pawn below a blue pawn with a wall behind the blue pawn, so the red pawn can step diagonally to the left or right of it"
          pawns={[{ r: 3, c: 2, tone: "mine" }, { r: 2, c: 2, tone: "theirs" }]} walls={[{ r: 1, c: 1, h: true, tone: "theirs" }]} hints={[{ r: 2, c: 1 }, { r: 2, c: 3 }, { r: 3, c: 1 }, { r: 3, c: 3 }, { r: 4, c: 2 }]} />
      </Section>

      <Section title="What the buttons and screens mean" open>
        <dl className="defs">
          <dt>← (top left)</dt><dd>Back to the home screen. An online game keeps going and you can <b>Resume</b> it later.</dd>
          <dt>⚑ Resign</dt><dd>Give up the game. Your opponent wins. It asks you to confirm first.</dd>
          <dt>🔊 / 🔇</dt><dd>Sound on or off. The choice is remembered.</dd>
          <dt>? (top)</dt><dd>Opens this guide without leaving your game.</dd>
          <dt>⟲ (two-player mode)</dt><dd>Turns the board towards whoever is moving.</dd>
          <dt>The two cards</dt><dd>One per player: the coloured bar is their pawn colour, the number is <b>walls left out of 10</b>. The card with the <b style={{ color: "var(--active)" }}>green border</b> is whose turn it is.</dd>
          <dt>The line under the cards</dt><dd>Shows the <b>latest move</b> (like "3. You: e1 → e2") or what's happening ("Computer is thinking…"). <b>Tap it</b> to see every move so far.</dd>
          <dt>White-outlined squares</dt><dd>Squares your pawn can move to right now. They only show on your turn.</dd>
          <dt>Dashed bar</dt><dd>A <b>preview</b> of where a wall would go. A <b>red</b> dashed bar means the spot is allowed; an <b>orange</b> bar means "not allowed here".</dd>
          <dt>Horizontal / Vertical</dt><dd>The two wall pieces at the bottom. See "How to place a wall" below.</dd>
          <dt>"time left" line (online)</dt><dd>Each player has {GAME_TIME_MS / 60000} minutes for the entire game. Your clock runs only on your turn and resumes with your remaining time. When your opponent runs out of time, a <b>"claim win"</b> button appears for you.</dd>
          <dt>Result screen</dt><dd>Shows who won plus moves, walls used and time. <b>Play again</b> (computer / one device), <b>Rematch</b> (online) or <b>Home</b>.</dd>
        </dl>
      </Section>

      <Section title="How to place a wall" open>
        <p><b>Drag it:</b> press and hold <b>Horizontal</b> or <b>Vertical</b>, then drag onto the board. The bar floats just above your finger and a dashed preview snaps to the nearest groove. <b>Let go to place it.</b> To cancel, drop it off the board.</p>
        <p><b>Or tap:</b> tap a <b>Horizontal</b> or <b>Vertical</b> button to choose the direction, then <b>tap a groove</b> (the thin gap between squares) once to preview and <b>tap the same spot again</b> to place it.</p>
        <p>Horizontal walls block moves up/down. Vertical walls block moves left/right.</p>
      </Section>

      <Section title="The game modes">
        <dl className="defs">
          <dt>Play the computer</dt><dd><b>Easy</b> makes mistakes, <b>Medium</b> plans one move ahead, <b>Hard</b> thinks several moves ahead. You always go first. No internet needed once the page has loaded.</dd>
          <dt>Two players, one device</dt><dd>Pass the phone or tablet back and forth: <Dot tone="mine" /> Player 1 starts at the bottom, <Dot tone="theirs" /> Player 2 at the top. Use ⟲ to turn the board towards whoever is moving. No sign-in, and results aren't saved.</dd>
          {onlineConfigured && (
            <>
              <dt>Quick match</dt><dd>Plays you against another real person who is searching right now. You wait on a "Looking for an opponent…" screen until someone joins. Tap <b>Cancel</b> to stop.</dd>
              <dt>Create room</dt><dd>Makes a private game for a friend. Send them the <b>code</b> or the <b>invite link</b> (the Share button does it for you).</dd>
              <dt>Join room</dt><dd>Type your friend's 8-character code and tap <b>Join room</b>, or just open their link.</dd>
              <dt>Resume game</dt><dd>Appears on the home screen if you left an unfinished online game. It takes you back.</dd>
              <dt>Rematch</dt><dd>After an online game, tap <b>Rematch</b>. Your opponent sees "wants a rematch" and taps <b>Accept</b>. You swap sides: whoever moved second now moves first.</dd>
              <dt>Leaderboard 🏆</dt><dd>The top 10 players by online wins. Your own row is highlighted.</dd>
              <dt>"3 online · 79 players joined"</dt><dd>How many people are playing right now, and how many have ever joined.</dd>
            </>
          )}
        </dl>
      </Section>

      {onlineConfigured && (
        <Section title="Accounts: guest or Google">
          <p>Online play needs an account, but it only takes one tap:</p>
          <ul>
            <li><b>Play as guest:</b> instant, no email. Your stats are saved on this device's guest account.</li>
            <li><b>Continue with Google:</b> your stats follow you to any device.</li>
          </ul>
          <p>A guest can open <b>Profile → Link Google account</b> and keep their stats. <b>Careful:</b> signing out of a guest account loses it for good.</p>
          <p>In <b>Profile</b> you can change your name and see your games, wins, losses and online wins. Games against the computer count towards your stats when you're signed in.</p>
        </Section>
      )}

      <Section title="Quick tips">
        <ul>
          <li>Don't spend all your walls early. They're most useful when your opponent is close to the finish.</li>
          <li>A wall that makes your opponent take a long detour is worth more than one that just blocks the straight path.</li>
          <li>Watch your own path too: your opponent's walls can lengthen your route.</li>
          <li>Install it like an app: on a phone open the browser menu and choose <b>Add to Home Screen</b> (on iPhone: Share → Add to Home Screen).</li>
        </ul>
      </Section>

      <Section title="Questions">
        <dl className="defs">
          <dt>I can't see any outlined squares.</dt><dd>It isn't your turn yet, or the game has ended. Look for the green border on your card.</dd>
          <dt>Why was my wall refused?</dt><dd>It overlaps or crosses another wall, or it would completely seal off a pawn. Try a different spot or direction.</dd>
          <dt>My opponent disappeared.</dt><dd>When their remaining game time reaches zero, tap <b>claim win</b>. You can also resign and start a new game.</dd>
          <dt>I closed the tab in the middle of a game.</dt><dd>Open the site again and tap <b>Resume game</b>.</dd>
          <dt>How do I look at earlier moves?</dt><dd>Tap the line under the cards for the full move list.</dd>
        </dl>
      </Section>
    </div>
  );
}

/** The guide as its own page (from the home screen). */
export function HelpPage({ onBack }: { onBack: () => void }) {
  return (
    <div className="page">
      <header className="topbar">
        <button className="icon" onClick={onBack} aria-label="Back">←</button>
        <h2 className="title">How to play</h2>
      </header>
      <HelpContent />
    </div>
  );
}

/** The guide as a sheet over a running game. */
export function HelpSheet({ onClose }: { onClose: () => void }) {
  return (
    <div className="overlay sheet-wrap" onClick={onClose}>
      <div className="sheet tall" role="dialog" aria-modal="true" aria-label="How to play" onClick={(e) => e.stopPropagation()}>
        <div className="grab" />
        <div className="sheet-head"><h2>How to play</h2><button className="icon" onClick={onClose} aria-label="Close">✕</button></div>
        <HelpContent />
      </div>
    </div>
  );
}
