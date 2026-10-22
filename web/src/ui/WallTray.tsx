import { useRef } from "react";

interface Props {
  /** Which orientation is currently selected. */
  selectedHorizontal: boolean;
  enabled: boolean;
  onSelect: (horizontal: boolean) => void;
  onDragStart: (horizontal: boolean, x: number, y: number) => void;
  onDragMove: (x: number, y: number) => void;
  onDragEnd: () => void;
  onDragCancel: () => void;
}

/**
 * The two wall pieces. Tap one to choose the orientation used when you tap a groove on the board;
 * press and drag one onto the board to place it there.
 */
export function WallTray(p: Props) {
  return (
    <div className="tray">
      <TrayButton label="Horizontal" horizontal selected={p.selectedHorizontal} {...p} />
      <TrayButton label="Vertical" horizontal={false} selected={!p.selectedHorizontal} {...p} />
    </div>
  );
}

function TrayButton({ label, horizontal, selected, enabled, onSelect, onDragStart, onDragMove, onDragEnd, onDragCancel }:
  Props & { label: string; horizontal: boolean; selected: boolean }) {
  const down = useRef<{ x: number; y: number; dragging: boolean } | null>(null);

  return (
    <button
      type="button"
      className={`tray-btn${selected ? " selected" : ""}`}
      disabled={!enabled}
      aria-label={`${label} wall. Drag onto the board to place it`}
      onPointerDown={(e) => {
        if (!enabled) return;
        e.currentTarget.setPointerCapture(e.pointerId);
        down.current = { x: e.clientX, y: e.clientY, dragging: false };
      }}
      onPointerMove={(e) => {
        const d = down.current;
        if (!d) return;
        if (!d.dragging && Math.hypot(e.clientX - d.x, e.clientY - d.y) > 6) {
          d.dragging = true;
          onSelect(horizontal);
          onDragStart(horizontal, e.clientX, e.clientY);
        } else if (d.dragging) {
          onDragMove(e.clientX, e.clientY);
        }
      }}
      onPointerUp={() => {
        const d = down.current;
        down.current = null;
        if (!d) return;
        if (d.dragging) onDragEnd();
        else onSelect(horizontal);
      }}
      onPointerCancel={() => {
        if (down.current?.dragging) onDragCancel();
        down.current = null;
      }}
    >
      <span className={`glyph ${horizontal ? "h" : "v"}`} />
      {label}
    </button>
  );
}
