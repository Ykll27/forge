#!/usr/bin/env bash
# Prepara o emulador, instala o APK com os recursos já no lugar e roda o roteiro de toques.
# Chamado pelo workflow ptbr-emulador.yml; o diretório de trabalho é a raiz do repositório.
set -uo pipefail

PKG=forge.app
ROOT=$(pwd)
OUT="$ROOT/emu/capturas"
mkdir -p "$OUT"

foto() {
    adb exec-out screencap -p > "$OUT/$1.png"
    echo "foto $1"
}
toque() {
    adb shell input tap "$1" "$2"
    echo "toque $1,$2"
}
deslize() {
    adb shell input swipe "$1" "$2" "$3" "$4" "${5:-300}"
}
espera() {
    sleep "$1"
}
texto() {
    adb shell input text "$1"
}
# Toca num elemento de tela nativa do Android pelo texto (não funciona dentro do jogo, que é OpenGL).
toque_texto() {
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    adb shell cat /sdcard/ui.xml > "$OUT/ui.xml" 2>/dev/null
    local pos
    pos=$(python3 - "$1" "$OUT/ui.xml" <<'EOF'
import re, sys
alvo, arq = sys.argv[1], sys.argv[2]
xml = open(arq, encoding="utf-8", errors="ignore").read()
m = re.search(r'text="%s"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % re.escape(alvo), xml)
print("" if not m else "%d %d" % ((int(m.group(1)) + int(m.group(3))) // 2, (int(m.group(2)) + int(m.group(4))) // 2))
EOF
)
    if [ -n "$pos" ]; then
        # shellcheck disable=SC2086
        adb shell input tap $pos
        echo "toque_texto '$1' em $pos"
    else
        echo "toque_texto: '$1' não encontrado"
    fi
}
estado() {
    {
        echo "== $1"
        adb shell dumpsys window displays | grep -iE "cur=|app=|rotation" | head -5
        adb shell dumpsys activity activities | grep -iE "mResumedActivity|topResumedActivity" | head -2
    } >> "$OUT/estado.txt" 2>&1
}

# tela e orientação parecidas com um celular simples, segurado deitado
adb shell wm size 720x1600
adb shell wm density 280
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1

adb install -r -g emu/Forge-PT-BR.apk

# recursos já instalados (o mesmo conteúdo que o app baixaria do assets.zip da release)
DIR=/sdcard/Android/obb/$PKG/Forge
adb shell mkdir -p "$DIR"
adb push emu/assets/. "$DIR/" > /dev/null
adb shell ls "$DIR" > "$OUT/pasta-forge.txt"

adb logcat -c
adb shell am start -n "$PKG/.Launcher"
estado inicio

# shellcheck source=roteiro.sh
source "$ROOT/.github/emulador/roteiro.sh"

estado fim
adb logcat -d > "$OUT/logcat.txt" 2>&1
grep -iE "FATAL|AndroidRuntime|Exception" "$OUT/logcat.txt" | head -80 > "$OUT/erros.txt" || true
exit 0
