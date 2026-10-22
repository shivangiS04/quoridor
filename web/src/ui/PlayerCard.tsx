interface Props { name: string; tone: "mine" | "theirs"; wallsLeft: number; active: boolean }

/** Name, the player's colour as a small bar, and walls left out of ten. The player on turn gets a green border. */
export function PlayerCard({ name, tone, wallsLeft, active }: Props) {
  return (
    <div className={`card${active ? " active" : ""}`} aria-label={`${name}, ${wallsLeft} of 10 walls left`}>
      <div className="card-name">{name}</div>
      <div className="card-walls">
        <span className={`dash ${tone}`} />
        <strong>{wallsLeft}</strong>
        <span className="muted"> /10</span>
      </div>
    </div>
  );
}
