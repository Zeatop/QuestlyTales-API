#!/usr/bin/env python3
"""
Convertit une histoire générée (JSON renvoyé par /api/stories/generate) en
diagramme Mermaid, pour visualiser les interconnexions entre nœuds.

Usage (macOS) :
    pbpaste | python3 scripts/story_to_mermaid.py        # depuis le presse-papier
    python3 scripts/story_to_mermaid.py histoire.json    # depuis un fichier

Colle ensuite la sortie dans https://mermaid.live
(ou un fichier .mmd ouvert avec l'extension Mermaid de VS Code).

Astuce : les DOUBLONS d'id sont signalés dans des commentaires en tête —
Mermaid fusionne les nœuds de même id, donc un graphe emmêlé à un endroit
précis trahit souvent un id réutilisé par le LLM.
"""
import json
import sys


def short(text, n=60):
    """Tronque et neutralise les caractères qui cassent un label Mermaid."""
    if not text:
        return ""
    text = " ".join(str(text).split())
    text = text.replace('"', "'").replace("[", "(").replace("]", ")")
    return (text[: n - 1] + "…") if len(text) > n else text


def node_summary(node):
    """Petit résumé du nœud : 1er ShowDialog (speaker + extrait)."""
    for action in node.get("actions") or []:
        if action.get("type") == "ShowDialog":
            p = action.get("params", {})
            return short(f"{p.get('speaker', '?')}: {p.get('value', '')}", 70)
    return ""


def main():
    raw = open(sys.argv[1], encoding="utf-8").read() if len(sys.argv) > 1 else sys.stdin.read()
    story = json.loads(raw)
    nodes = story.get("nodes", [])

    # Détection des doublons d'id (bug fréquent côté LLM)
    seen, dups = set(), set()
    for n in nodes:
        nid = n.get("id")
        (dups if nid in seen else seen).add(nid)

    out = ["flowchart TD"]
    if dups:
        out.append(f"  %% ⚠️ IDS EN DOUBLON (nœuds fusionnés par Mermaid) : {sorted(dups)}")

    # Déclaration des nœuds
    for n in nodes:
        nid = n.get("id")
        is_end = not (n.get("choices") or [])
        label = node_summary(n)
        text = f"#{nid}" + (f"<br/>{label}" if label else "")
        if is_end:
            out.append(f'  n{nid}(["{text}"])')      # fin = forme arrondie
        else:
            out.append(f'  n{nid}["{text}"]')

    # Arêtes = choix
    for n in nodes:
        nid = n.get("id")
        for c in n.get("choices") or []:
            dst = c.get("nextNodeId")
            if dst is None:
                continue
            label = short(c.get("choiceText", ""), 40)
            out.append(f'  n{nid} -->|"{label}"| n{dst}')

    # Met les fins en évidence
    ends = [n.get("id") for n in nodes if not (n.get("choices") or [])]
    for nid in ends:
        out.append(f"  style n{nid} fill:#E74C3C,color:#fff")

    print("\n".join(out))


if __name__ == "__main__":
    main()
