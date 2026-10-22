import { type RefObject } from "react";
import { BOARD, goalRow, type GameState } from "../game/rules.ts";
import { metrics, SIZE, slotAt, wallBar, wallOrigin } from "../game/geometry.ts";
import type { WallRef } from "../game/useLocalGame.ts";

export interface DragTarget extends WallRef { valid: boolean }

interface Props {
  game: GameState;
  me: number;
  validMoves: { row: number; col: number }[];
  owners: Map<string, number>;
  lastWall?: WallRef | null;
  preview: WallRef | null;
  target: DragTarget | null;
  invalid: (WallRef & { tick: number }) | null;
  svgRef: RefObject<SVGSVGElement | null>;
  onCell: (row: number, col: number) => void;
  onGroove: (row: number, col: number) => void;
}

const m = metrics();
const key = (h: boolean, r: number, c: number) => `${h ? "h" : "v"}${r},${c}`;

/** The board: flat tiles, flat pawns, flat wall bars, all drawn in a 1000×1000 SVG. */
export function Board({ game, me, validMoves, owners, lastWall, preview, target, invalid, svgRef, onCell, onGroove }: Props) {
  const handleClick = (e: React.MouseEvent<SVGSVGElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const slot = slotAt(((e.clientX - rect.left) * SIZE) / rect.width, ((e.clientY - rect.top) * SIZE) / rect.height, m);
    if (slot?.kind === "cell") onCell(slot.row, slot.col);
    else if (slot?.kind === "groove") onGroove(slot.row, slot.col);
  };
  const ownerClass = (h: boolean, r: number, c: number) => {
    const o = owners.get(key(h, r, c));
    return o === undefined ? "wall neutral" : o === me ? "wall mine" : "wall theirs";
  };
  const pawn = (pi: number) => {
    const p = game.players[pi];
    return `translate(${p.col * m.stride + m.cell / 2}px, ${p.row * m.stride + m.cell / 2}px)`;
  };
  const myGoal = goalRow(me), theirGoal = goalRow(1 - me);
  const bar = (h: boolean, r: number, c: number) => {
    const { w, h: ht } = wallBar(h, m), o = wallOrigin(r, c, h, m);
    return { x: o.x, y: o.y, width: w, height: ht, rx: Math.min(w, ht) / 2 };
  };

  return (
    <svg ref={svgRef} className="board" viewBox={`0 0 ${SIZE} ${SIZE}`} role="img" aria-label="Quoridor board" onClick={handleClick}>
      <defs>
        <clipPath id="boardClip"><rect width={SIZE} height={SIZE} rx={28} /></clipPath>
      </defs>
      <g clipPath="url(#boardClip)">
        {Array.from({ length: BOARD * BOARD }, (_, i) => {
          const r = Math.floor(i / BOARD), c = i % BOARD;
          return (
            <g key={i}>
              <rect className="tile" x={c * m.stride} y={r * m.stride} width={m.cell} height={m.cell} rx={12} />
              {r === myGoal && <rect className="goal mine" x={c * m.stride} y={r * m.stride} width={m.cell} height={m.cell} rx={12} />}
              {r === theirGoal && <rect className="goal theirs" x={c * m.stride} y={r * m.stride} width={m.cell} height={m.cell} rx={12} />}
            </g>
          );
        })}
      </g>

      {validMoves.map((v) => (
        <rect key={`v${v.row},${v.col}`} className="hint" x={v.col * m.stride + 2} y={v.row * m.stride + 2}
          width={m.cell - 4} height={m.cell - 4} rx={12} />
      ))}

      {game.walls.horizontal.map((w) => {
        const isNew = lastWall?.horizontal && lastWall.row === w.row && lastWall.col === w.col;
        return <rect key={`h${w.row},${w.col}`} className={`${ownerClass(true, w.row, w.col)}${isNew ? " fresh" : ""}`} {...bar(true, w.row, w.col)} />;
      })}
      {game.walls.vertical.map((w) => {
        const isNew = lastWall && !lastWall.horizontal && lastWall.row === w.row && lastWall.col === w.col;
        return <rect key={`v${w.row},${w.col}`} className={`${ownerClass(false, w.row, w.col)}${isNew ? " fresh" : ""}`} {...bar(false, w.row, w.col)} />;
      })}

      {preview && <rect className="ghost" {...bar(preview.horizontal, preview.row, preview.col)} />}
      {target && <rect className={`ghost${target.valid ? "" : " bad"}`} {...bar(target.horizontal, target.row, target.col)} />}
      {invalid && <rect key={invalid.tick} className="ghost bad flash" {...bar(invalid.horizontal, invalid.row, invalid.col)} />}

      <g className="pawn" style={{ transform: pawn(1 - me) }}><circle className="theirs" r={m.cell * 0.3} /></g>
      <g className="pawn" style={{ transform: pawn(me) }}><circle className="mine" r={m.cell * 0.3} /></g>
    </svg>
  );
}
