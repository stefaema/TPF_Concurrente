#!/usr/bin/env python3
"""
Verifica T-invariantes sobre el log crudo.
Al matchear un invariante, conserva las transiciones intercaladas de otros clientes
y solo elimina las del invariante matcheado.
Al final, lo que sobre en el log son errores reales.
"""

import re
import sys
from pathlib import Path

# I1: 0 1 3 4 7 8 11  (inferior + rechazado)
# I2: 0 1 3 4 6 9 10 11  (inferior + aprobado)
# I3: 0 1 2 5 7 8 11  (superior + rechazado)
# I4: 0 1 2 5 6 9 10 11  (superior + aprobado)
#
# Grupos capturados (.*?) = contenido intercalado que se CONSERVA en el log.
# Grupos 3 y 4 indican rama agente (inferior/superior).
# Grupos 6 y 7 indican rama decisión (cancelado/aprobado).
REGEX = re.compile(
    r"\b0\b(.*?)\b1\b(.*?)"
    r"(?:\b3\b(.*?)\b4\b|\b2\b(.*?)\b5\b)(.*?)"
    r"(?:\b7\b(.*?)\b8\b|\b6\b(.*?)\b9\b(.*?)\b10\b)(.*?)"
    r"\b11\b",
    re.DOTALL
)

def clasificar(m: re.Match) -> str:
    inferior = m.group(3) is not None   # rama 3→4
    aprobado = m.group(7) is not None   # rama 6→9→10
    if     inferior and not aprobado: return "I1"
    if     inferior and     aprobado: return "I2"
    if not inferior and not aprobado: return "I3"
    return "I4"

def main() -> None:
    ruta = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("logs/log.txt")
    if not ruta.exists():
        print(f"Error: no se encontró '{ruta}'", file=sys.stderr)
        sys.exit(1)

    # Leer el log como una sola línea, quitando la T de cada token
    log = ruta.read_text(encoding="utf-8").replace("\n", " ").replace("T", "")

    conteos = {"I1": 0, "I2": 0, "I3": 0, "I4": 0}

    while True:
        m = REGEX.search(log)
        if not m:
            break
        conteos[clasificar(m)] += 1
        # Conservar solo el contenido intercalado (los grupos .*?), descartar los tokens del invariante
        contenido_intercalado = "".join(g for g in m.groups() if g is not None)
        log = log[:m.start()] + contenido_intercalado + log[m.end():]

    total = sum(conteos.values())
    SEP = "=" * 52
    print(SEP)
    print("  ANÁLISIS DE T-INVARIANTES")
    print(SEP)
    print(f"  I1 (inferior + rechazado) : {conteos['I1']:3d}" + (f"  ({100.0*conteos['I1']/total:.1f}%)" if total else ""))
    print(f"  I2 (inferior + aprobado)  : {conteos['I2']:3d}" + (f"  ({100.0*conteos['I2']/total:.1f}%)" if total else ""))
    print(f"  I3 (superior + rechazado) : {conteos['I3']:3d}" + (f"  ({100.0*conteos['I3']/total:.1f}%)" if total else ""))
    print(f"  I4 (superior + aprobado)  : {conteos['I4']:3d}" + (f"  ({100.0*conteos['I4']/total:.1f}%)" if total else ""))

    if total:
        sup = conteos["I3"] + conteos["I4"]
        inf = conteos["I1"] + conteos["I2"]
        apr = conteos["I2"] + conteos["I4"]
        can = conteos["I1"] + conteos["I3"]
        print()
        print(f"  Total                   : {total}")
        print(f"  Agente superior (I3+I4) : {sup:3d}  ({100.0*sup/total:.1f}%)")
        print(f"  Agente inferior (I1+I2) : {inf:3d}  ({100.0*inf/total:.1f}%)")
        print(f"  Confirmadas     (I2+I4) : {apr:3d}  ({100.0*apr/total:.1f}%)")
        print(f"  Canceladas      (I1+I3) : {can:3d}  ({100.0*can/total:.1f}%)")

    print(SEP)

    # Lo que sobre son tokens que no pertenecen a ningún invariante completo
    sobrantes = re.findall(r'\d+', log)
    if sobrantes:
        linea = ' '.join(sobrantes)
        print(f"\n  Sobrantes: {linea}")
        ruta.write_text("Sobrantes: " + linea + "\n", encoding="utf-8")
    else:
        print(f"\n  [OK] Log vacío — todos los {total} invariantes verificados.")
        ruta.write_text("", encoding="utf-8")

if __name__ == "__main__":
    main()
